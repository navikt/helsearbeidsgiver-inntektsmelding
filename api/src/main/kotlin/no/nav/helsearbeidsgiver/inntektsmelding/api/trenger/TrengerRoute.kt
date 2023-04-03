package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.authorization.AltinnAuthorizer
import no.nav.helsearbeidsgiver.inntektsmelding.api.authorization.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.authorization.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.cache.LocalCache
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.TrengerRoute(
    cache: LocalCache<String>,
    authorizer: AltinnAuthorizer
) {
    val trengerProducer = TrengerProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = call.receive<TrengerRequest>()
            logger.info("Henter data for uuid: ${request.uuid}")
            try {
                cache.getOrNull(request.uuid)?.let { authorize(authorizer, it) }
                request.validate()
                val uuid = trengerProducer.publish(request)
                val resultat = redis.getResultat(uuid.toString(), 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")
                val mapper = TrengerMapper(resultat)

                val orgnr = cache.get(request.uuid) {
                    mapper.mapOrgNummer()
                }
                authorize(authorizer, orgnr)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ManglerAltinnRettigheterException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Mangler Rettigheter i Altinn for organisasjon"
                )
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $request.uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $request.uuid")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(request.uuid))
            }
        }
    }
}
