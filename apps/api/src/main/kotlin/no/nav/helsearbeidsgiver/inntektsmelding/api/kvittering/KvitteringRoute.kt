@file:UseSerializers(OffsetDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.fjernLedendeSlash
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondNotFound
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.time.OffsetDateTime
import java.util.UUID

fun Route.kvittering(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val kvitteringProducer = KvitteringProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.Kvittering).let(::RedisPoller)

    get(Routes.KVITTERING) {
        val transaksjonId = UUID.randomUUID()

        val forespoerselId =
            call.parameters["uuid"]
                ?.let(::fjernLedendeSlash)
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (forespoerselId == null) {
            "Ugyldig parameter: ${call.parameters["uuid"]}".let {
                logger.warn(it)
                respondBadRequest(it, String.serializer())
            }
        } else {
            logger.info("Henter data for forespørselId: $forespoerselId")
            Metrics.kvitteringEndpoint.recordTime(Route::kvittering) {
                try {
                    tilgangskontroll.validerTilgangTilForespoersel(call.request, forespoerselId)

                    kvitteringProducer.publish(transaksjonId, forespoerselId)
                    val resultatJson = redisPoller.hent(transaksjonId)

                    val resultat = resultatJson.success?.fromJson(KvitteringResultat.serializer())
                    if (resultat != null) {
                        sikkerLogger.info("Hentet kvittering for '$forespoerselId'.\n${resultatJson.success?.toPretty()}")

                        when (val lagret = resultat.lagret) {
                            is LagretInntektsmelding.Skjema -> {
                                val skjemaResponse = lagResponse(resultat.forespoersel, resultat.sykmeldtNavn, resultat.orgNavn, lagret)
                                respondOk(skjemaResponse, KvitteringResponse.serializer())
                            }
                            is LagretInntektsmelding.Ekstern -> {
                                val eksternResponse = lagResponse(lagret)
                                respondOk(eksternResponse, KvitteringResponse.serializer())
                            }
                            null ->
                                respondNotFound("Kvittering ikke funnet for forespørselId: $forespoerselId", String.serializer())
                        }
                    } else {
                        val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                        respondInternalServerError(feilmelding, String.serializer())
                    }
                } catch (e: ManglerAltinnRettigheterException) {
                    respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
                } catch (e: SerializationException) {
                    "Kunne ikke parse json-resultat for forespørselId: $forespoerselId".let {
                        logger.error(it)
                        sikkerLogger.error(it, e)
                        respondInternalServerError(JsonErrorResponse(forespoerselId.toString()), JsonErrorResponse.serializer())
                    }
                } catch (_: RedisPollerTimeoutException) {
                    logger.error("Fikk timeout for forespørselId: $forespoerselId")
                    respondInternalServerError(RedisTimeoutResponse(forespoerselId), RedisTimeoutResponse.serializer())
                }
            }
        }
    }
}

@Serializable
private data class KvitteringResponse(
    val kvitteringNavNo: NavNo?,
    val kvitteringEkstern: Ekstern?,
) {
    @Serializable
    data class NavNo(
        val sykmeldt: Sykmeldt,
        val avsender: Avsender,
        val sykmeldingsperioder: List<Periode>,
        val skjema: SkjemaInntektsmelding,
        val mottatt: OffsetDateTime,
    )

    @Serializable
    data class Ekstern(
        val avsenderSystem: String,
        val referanse: String,
        val mottatt: OffsetDateTime,
    )
}

private fun lagResponse(
    forespoersel: Forespoersel,
    sykmeldtNavn: String,
    orgNavn: String,
    lagret: LagretInntektsmelding.Skjema,
): KvitteringResponse =
    KvitteringResponse(
        kvitteringNavNo =
            KvitteringResponse.NavNo(
                sykmeldt =
                    Sykmeldt(
                        fnr = forespoersel.fnr,
                        navn = sykmeldtNavn,
                    ),
                avsender =
                    Avsender(
                        orgnr = forespoersel.orgnr,
                        orgNavn = orgNavn,
                        navn = lagret.avsenderNavn ?: "Ukjent navn",
                        tlf = lagret.skjema.avsenderTlf,
                    ),
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
                skjema = lagret.skjema,
                mottatt = lagret.mottatt.toOffsetDateTimeOslo(),
            ),
        kvitteringEkstern = null,
    )

private fun lagResponse(lagret: LagretInntektsmelding.Ekstern): KvitteringResponse =
    KvitteringResponse(
        kvitteringNavNo = null,
        kvitteringEkstern =
            KvitteringResponse.Ekstern(
                avsenderSystem = lagret.ekstern.avsenderSystemNavn,
                referanse = lagret.ekstern.arkivreferanse,
                mottatt = lagret.ekstern.tidspunkt.toOffsetDateTimeOslo(),
            ),
    )
