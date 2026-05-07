package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold.AnsettelsesforholdResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

fun Route.hentArbeidsforholdSelvbestemtRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentArbeidsforholdSelvbestemt).let(::RedisPoller)

    post(Routes.HENT_ARBEIDSFORHOLD_SELVBESTEMT) {
        val kontekstId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.HENT_ARBEIDSFORHOLD_SELVBESTEMT),
            Log.kontekstId(kontekstId),
        ) {
            readRequestOrError(kontekstId, HentArbeidsforholdSelvbestemtRequest.serializer()) { request ->
                validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, request.orgnr) {
                    producer.sendRequestEvent(kontekstId, request)

                    hentResultatFraRedisOrError(
                        redisPoller = redisPoller,
                        kontekstId = kontekstId,
                        logOnFailure = "Klarte ikke hente arbeidsforhold for selvbestemt.",
                        successSerializer = Ansettelsesforhold.serializer().list(),
                    ) { ansettelsesforhold ->
                        val response =
                            HentArbeidsforholdSelvbestemtResponse(
                                ansettelsesforhold = ansettelsesforhold.map(AnsettelsesforholdResponse::fra),
                            )

                        "Arbeidsforhold for selvbestemt hentet OK.".also {
                            logger.info(it)
                            sikkerLogger.info("$it\n${response.toJson(HentArbeidsforholdSelvbestemtResponse.serializer()).toPretty()}")
                        }

                        respondOk(response, HentArbeidsforholdSelvbestemtResponse.serializer())
                    }
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    request: HentArbeidsforholdSelvbestemtRequest,
) {
    send(
        key = request.sykmeldtFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                        Key.SYKMELDT_FNR to request.sykmeldtFnr.toJson(),
                        Key.PERIODE to Periode(request.fom, request.tom).toJson(Periode.serializer()),
                    ).toJson(),
            ),
    )
}
