package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.domene.KvitteringResultat
import no.nav.hag.simba.utils.felles.domene.LagretInntektsmelding
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondNotFound
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.date.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

fun Route.kvittering(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.Kvittering).let(::RedisPoller)

    get(Routes.KVITTERING) {
        val kontekstId = UUID.randomUUID()

        val forespoerselId =
            call.parameters["forespoerselId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (forespoerselId == null) {
            "Ugyldig parameter: ${call.parameters["forespoerselId"]}".let {
                logger.warn(it)
                respondBadRequest(it, String.serializer())
            }
        } else {
            logger.info("Henter data for forespørselId: $forespoerselId")
            try {
                tilgangskontroll.validerTilgangTilForespoersel(call.request, forespoerselId)

                producer.sendRequestEvent(kontekstId, forespoerselId)
                val resultatJson = redisPoller.hent(kontekstId)

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

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    forespoerselId: UUID,
) {
    send(
        key = forespoerselId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    ).toJson(),
            ),
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
