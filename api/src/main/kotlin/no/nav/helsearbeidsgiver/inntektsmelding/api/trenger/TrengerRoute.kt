package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.TrengerRoute() {
    val trengerProducer = TrengerProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = call.receive<TrengerRequest>()
            logger.info("Henter data for uuid: ${request.uuid}")
            val innloggerFnr = "10107400090" // TODO bytt ut med ekte fnr
            try {
                request.validate()
                val tilgangId = tilgangProducer.publish(innloggerFnr, request.uuid)
                val resultatTilgang = redis.getResultat(tilgangId.toString(), 10, 500)
                val tilgangskontroll = resultatTilgang.TILGANGSKONTROLL
                if (tilgangskontroll?.value == Tilgang.HAR_TILGANG) {
                    val trengerId = trengerProducer.publish(request)
                    val resultat = redis.getResultat(trengerId.toString(), 10, 500)
                    sikkerlogg.info("Fikk resultat: $resultat")
                    val mapper = TrengerMapper(resultat)
                    call.respond(mapper.getStatus(), mapper.getResponse())
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Du har ikke rettigheter for organisasjon.")
                }
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for ${request.uuid}")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for ${request.uuid}")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(request.uuid))
            }
        }
    }
}
