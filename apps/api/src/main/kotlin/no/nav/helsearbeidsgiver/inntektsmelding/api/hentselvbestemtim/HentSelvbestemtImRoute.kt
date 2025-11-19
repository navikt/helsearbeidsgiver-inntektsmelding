package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

fun Route.hentSelvbestemtImRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentSelvbestemtIm).let(::RedisPoller)

    get(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID) {
        val kontekstId = UUID.randomUUID()

        val selvbestemtId =
            call.parameters["selvbestemtId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (selvbestemtId == null) {
            "Ugyldig parameter: '${call.parameters["selvbestemtId"]}'.".let {
                logger.error(it)
                sikkerLogger.error(it)
                respondBadRequest(it)
            }
        } else {
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID),
                Log.kontekstId(kontekstId),
                Log.selvbestemtId(selvbestemtId),
            ) {
                producer.sendRequestEvent(kontekstId, selvbestemtId)

                val result = redisPoller.hent(kontekstId)
                if (result != null) {
                    val inntektsmelding = result.success?.fromJson(Inntektsmelding.serializer())

                    if (inntektsmelding != null) {
                        tilgangskontroll.validerTilgangTilOrg(call.request, inntektsmelding.avsender.orgnr)
                        sendOkResponse(inntektsmelding.fjernNavnHvisIngenArbeidsforhold())
                    } else {
                        val feilmelding = result.failure?.fromJson(String.serializer())

                        "Klarte ikke hente inntektsmelding pga. feil.".also {
                            logger.error(it)
                            sikkerLogger.error("$it Feilmelding: '$feilmelding'")
                        }

                        respondInternalServerError(ErrorResponse.Unknown(kontekstId))
                    }
                } else {
                    respondInternalServerError(
                        ErrorResponse.RedisTimeout(
                            kontekstId = kontekstId,
                            inntektsmeldingTypeId = selvbestemtId,
                        ),
                    )
                }
            }
        }
    }
}

private fun Inntektsmelding.fjernNavnHvisIngenArbeidsforhold() =
    if (type is Inntektsmelding.Type.Fisker || type is Inntektsmelding.Type.UtenArbeidsforhold) {
        copy(sykmeldt = sykmeldt.copy(navn = "Ukjent navn"))
    } else {
        this
    }

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    selvbestemtId: UUID,
) {
    send(
        key = selvbestemtId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                    ).toJson(),
            ),
    )
}

private suspend fun RoutingContext.sendOkResponse(inntektsmelding: Inntektsmelding) {
    val response = HentSelvbestemtImResponseSuccess(inntektsmelding).toJson(HentSelvbestemtImResponseSuccess.serializer())

    "Selvbestemt inntektsmelding hentet OK.".also {
        logger.info(it)
        sikkerLogger.info("$it\n${response.toPretty()}")
    }

    respondOk(ResultJson(success = response), ResultJson.serializer())
}
