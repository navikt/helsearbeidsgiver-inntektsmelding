package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.hag.simba.kontrakt.resultat.forespoersel.HentForespoerselResultat
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedis
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readPathParam
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

fun Route.hentForespoersel(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoersel).let(::RedisPoller)

    get(Routes.HENT_FORESPOERSEL) {
        val kontekstId = UUID.randomUUID()

        readPathParam(kontekstId, Routes.Params.forespoerselId) { forespoerselId ->
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.HENT_FORESPOERSEL),
                Log.kontekstId(kontekstId),
                Log.forespoerselId(forespoerselId),
            ) {
                validerTilgangForespoersel(tilgangskontroll, kontekstId, forespoerselId) {
                    "Henter forespørsel.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    producer.sendRequestEvent(
                        kontekstId = kontekstId,
                        forespoerselId = forespoerselId,
                        arbeidsgiverFnr = call.request.lesFnrFraAuthToken(),
                    )

                    hentResultatFraRedis(
                        redisPoller = redisPoller,
                        kontekstId = kontekstId,
                        logOnFailure = "Klarte ikke hente forespørsel.",
                        successSerializer = HentForespoerselResultat.serializer(),
                    ) { success ->
                        val response = success.toResponse()

                        "Forespørsel hentet OK.".also {
                            logger.info(it)
                            sikkerLogger.info("$it\n${response.toJson(HentForespoerselResponse.serializer()).toPretty()}")
                        }

                        respondOk(response, HentForespoerselResponse.serializer())
                    }
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    forespoerselId: UUID,
    arbeidsgiverFnr: Fnr,
) {
    send(
        key = forespoerselId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                    ).toJson(),
            ),
    )
}

private fun HentForespoerselResultat.toResponse(): HentForespoerselResponse =
    HentForespoerselResponse(
        sykmeldt =
            Sykmeldt(
                fnr = forespoersel.fnr,
                navn = sykmeldtNavn,
            ),
        avsender =
            Avsender(
                orgnr = forespoersel.orgnr,
                orgNavn = orgNavn,
                navn = avsenderNavn,
            ),
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        bestemmendeFravaersdag = forespoersel.forslagBestemmendeFravaersdag(),
        eksternInntektsdato = forespoersel.eksternInntektsdato(),
        inntekt =
            inntekt?.let {
                Inntekt(
                    gjennomsnitt = it.gjennomsnitt(),
                    historikk = it,
                )
            },
        forespurtData = forespoersel.forespurtData,
        erBesvart = forespoersel.erBesvart,
        erBegrenset = forespoersel.erBegrenset,
    )
