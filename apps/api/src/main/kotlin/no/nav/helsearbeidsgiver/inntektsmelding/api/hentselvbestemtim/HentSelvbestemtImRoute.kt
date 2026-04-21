package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readPathParamOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

fun Route.hentSelvbestemtImRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentSelvbestemtIm).let(::RedisPoller)

    get(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID) {
        val kontekstId = UUID.randomUUID()

        readPathParamOrError(kontekstId, Routes.Params.selvbestemtId) { selvbestemtId ->
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID),
                Log.kontekstId(kontekstId),
                Log.selvbestemtId(selvbestemtId),
            ) {
                producer.sendRequestEvent(kontekstId, selvbestemtId)

                hentResultatFraRedisOrError(
                    redisPoller = redisPoller,
                    kontekstId = kontekstId,
                    inntektsmeldingTypeId = selvbestemtId,
                    logOnFailure = "Klarte ikke hente selvbestemt inntektsmelding pga. feil.",
                    successSerializer = Inntektsmelding.serializer(),
                ) { inntektsmelding ->
                    validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, inntektsmelding.avsender.orgnr) {
                        val response =
                            HentSelvbestemtImResponseSuccess(inntektsmelding.fjernNavnHvisIngenArbeidsforhold())
                                .toJson(HentSelvbestemtImResponseSuccess.serializer())

                        "Selvbestemt inntektsmelding hentet OK.".also {
                            logger.info(it)
                            sikkerLogger.info("$it\n${response.toPretty()}")
                        }

                        // TODO trenger ikke å wrappe i ResultJson (må endres i frontend først)
                        respondOk(ResultJson(success = response), ResultJson.serializer())
                    }
                }
            }
        }
    }
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

private fun Inntektsmelding.fjernNavnHvisIngenArbeidsforhold() =
    if (type is Inntektsmelding.Type.Fisker || type is Inntektsmelding.Type.UtenArbeidsforhold) {
        copy(sykmeldt = sykmeldt.copy(navn = Tekst.UKJENT_NAVN))
    } else {
        this
    }
