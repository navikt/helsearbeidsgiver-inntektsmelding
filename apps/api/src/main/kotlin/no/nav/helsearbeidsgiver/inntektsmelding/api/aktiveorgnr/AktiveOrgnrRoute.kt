package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hag.simba.kontrakt.domene.arbeidsgiver.AktiveArbeidsgivere
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedis
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

fun Route.aktiveOrgnrRoute(
    producer: Producer,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.AktiveOrgnr).let(::RedisPoller)

    post(Routes.AKTIVEORGNR) {
        val kontekstId = UUID.randomUUID()

        readRequest(
            kontekstId,
            AktiveOrgnrRequest.serializer(),
        ) { request ->
            producer.sendRequestEvent(
                kontekstId = kontekstId,
                avsenderFnr = call.request.lesFnrFraAuthToken(),
                sykmeldtFnr = request.sykmeldtFnr,
            )

            hentResultatFraRedis(
                redisPoller = redisPoller,
                kontekstId = kontekstId,
                logOnFailure = "Klarte ikke hente aktive arbeidsforhold pga. feil.",
                successSerializer = AktiveArbeidsgivere.serializer(),
            ) {
                if (it.arbeidsgivere.isEmpty()) {
                    respondError(ErrorResponse.NotFound(kontekstId, "Fant ingen arbeidsforhold."))
                } else {
                    respondOk(it.toResponse(), AktiveOrgnrResponse.serializer())
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    avsenderFnr: Fnr,
    sykmeldtFnr: Fnr,
) {
    send(
        key = sykmeldtFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FNR to sykmeldtFnr.toJson(),
                        Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                    ).toJson(),
            ),
    )
}

private fun AktiveArbeidsgivere.toResponse(): AktiveOrgnrResponse =
    AktiveOrgnrResponse(
        fulltNavn = sykmeldtNavn,
        avsenderNavn = avsenderNavn.orEmpty(),
        underenheter =
            arbeidsgivere.map {
                GyldigUnderenhet(
                    orgnrUnderenhet = it.orgnr,
                    virksomhetsnavn = it.orgNavn,
                )
            },
    )
