package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException
import java.time.LocalDate

fun RouteExtra.TrengerRoute() {
    val trengerProducer = TrengerProducer(connection)
    val preutfyltProducer = PreutfyltProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = call.receive<TrengerRequest>()
            val uuid = request.uuid
            logger.info("Henter data for uuid: $uuid")
            try {
                // Valider requesten
                request.validate()
                val inntektResponse = if ("test".equals(uuid)) {
                    TrengerInntektResponse(
                        uuid,
                        "810007982",
                        "22506614191",
                        listOf(Periode(LocalDate.now().minusDays(6), LocalDate.now())),
                        listOf(Periode(LocalDate.now().minusDays(12), LocalDate.now()))
                    )
                } else {
                    // Hent orgnr og fnr basert på request
                    val uuidTrenger = trengerProducer.publish(request)
                    val resultatTrengerInntekt = redis.getResultat(uuidTrenger, 10, 500)
                    val trengerMapper = TrengerMapper(uuidTrenger, resultatTrengerInntekt, request)
                    trengerMapper.getResponse()
                }
                sikkerlogg.info("Fikk inntekt: $inntektResponse")
                // Hent ferdig utfylt
                val preutfyltRequest = PreutfyltRequest(inntektResponse.orgnr, inntektResponse.fnr)
                val preutfyltUuid = preutfyltProducer.publish(preutfyltRequest)
                logger.info("Publiserte behov uuid: $preutfyltUuid")
                val resultatPreutfylt = redis.getResultat(preutfyltUuid, 10, 500)
                sikkerlogg.info("Fikk preutfylt resultat: $resultatPreutfylt")
                val mapper = PreutfyltMapper(preutfyltUuid, resultatPreutfylt, preutfyltRequest, inntektResponse.sykemeldingsperioder)
                sikkerlogg.info("Klarte mappe resultat: $resultatPreutfylt")
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $uuid")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(uuid))
            }
        }
    }
}
