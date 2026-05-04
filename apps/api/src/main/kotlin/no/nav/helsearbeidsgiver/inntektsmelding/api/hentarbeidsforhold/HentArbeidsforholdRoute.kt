package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangForespoerselOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readPathParamOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

fun Route.hentArbeidsforholdRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentArbeidsforhold).let(::RedisPoller)

    get(Routes.HENT_ARBEIDSFORHOLD) {
        val kontekstId = UUID.randomUUID()

        readPathParamOrError(kontekstId, Routes.Params.forespoerselId) { forespoerselId ->

            MdcUtils.withLogFields(
                Log.apiRoute(Routes.HENT_ARBEIDSFORHOLD),
                Log.kontekstId(kontekstId),
                Log.forespoerselId(forespoerselId),
            ) {
                validerTilgangForespoerselOrError(tilgangskontroll, kontekstId, forespoerselId) {
                    producer.sendRequestEvent(kontekstId, forespoerselId)

                    hentResultatFraRedisOrError(
                        redisPoller = redisPoller,
                        kontekstId = kontekstId,
                        inntektsmeldingTypeId = forespoerselId,
                        logOnFailure = "Klarte ikke hente arbeidsforholdsdata.",
                        successSerializer = Ansettelsesforhold.serializer().set(),
                    ) { ansettelsesforhold ->
                        val response = HentArbeidsforholdResponse(ansettelsesforhold)
                        val responseJson = response.toJson(HentArbeidsforholdResponse.serializer())

                        "Arbeidsforholdsdata hentet OK.".also {
                            logger.info(it)
                            sikkerLogger.info("$it\n${responseJson.toPretty()}")
                        }

                        respondOk(response, HentArbeidsforholdResponse.serializer())
                    }
                }
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
                Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    ).toJson(),
            ),
    )
}
