package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
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

        val forespoerselId =
            call.parameters["forespoerselId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (forespoerselId == null) {
            "Ugyldig parameter: '${call.parameters["forespoerselId"]}'.".let {
                logger.error(it)
                sikkerLogger.error(it)
                respondBadRequest(it)
            }
        } else {
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.HENT_ARBEIDSFORHOLD),
                Log.kontekstId(kontekstId),
                Log.forespoerselId(forespoerselId),
            ) {
                try {
                    tilgangskontroll.validerTilgangTilForespoersel(call.request, forespoerselId)
                } catch (_: ManglerAltinnRettigheterException) {
                    respondForbidden("Mangler rettigheter for organisasjon.")
                    return@withLogFields
                }

                producer.sendRequestEvent(kontekstId, forespoerselId)

                val result = redisPoller.hent(kontekstId)

                if (result != null) {
                    val ansettelsesperioder = result.success?.fromJson(ListSerializer(PeriodeAapen.serializer()))

                    if (ansettelsesperioder != null) {
                        val response = HentArbeidsforholdResponse(ansettelsesperioder)
                        val responseJson = response.toJson(HentArbeidsforholdResponse.serializer())

                        "Arbeidsforhold-data hentet OK.".also {
                            logger.info(it)
                            sikkerLogger.info("$it\n${responseJson.toPretty()}")
                        }

                        respondOk(response, HentArbeidsforholdResponse.serializer())
                    } else {
                        val feilmelding = result.failure?.fromJson(String.serializer())

                        "Klarte ikke hente arbeidsforhold-data.".also {
                            logger.error(it)
                            sikkerLogger.error("$it Feilmelding: '$feilmelding'")
                        }

                        respondInternalServerError(ErrorResponse.Unknown(kontekstId))
                    }
                } else {
                    respondInternalServerError(ErrorResponse.RedisTimeout(kontekstId = kontekstId))
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
                Key.EVENT_NAME to EventName.AKTIVE_ARBEIDSFORHOLD_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    ).toJson(),
            ),
    )
}
