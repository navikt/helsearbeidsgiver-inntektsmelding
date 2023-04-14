package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.cache.LocalCache
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.mapInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.KvitteringRoute(cache: LocalCache<Tilgang>) {
    val kvitteringProducer = KvitteringProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.KVITTERING) {
        get {
            val foresporselId = call.parameters["uuid"].orEmpty()
            if (foresporselId.isEmpty() || foresporselId.length != 36) {
                logger.warn("Ugyldig parameter: $foresporselId")
                call.respond(HttpStatusCode.BadRequest)
            }
//            try { // TODO hvorfor funker ikke dette..?
//                val f = UUID.fromString(foresporselId)
//            } catch (iae: IllegalArgumentException) {
//                logger.warn("Ugyldig parameter: ${foresporselId}")
//                call.respond(HttpStatusCode.BadRequest)
//            }
            logger.info("Henter data for uuid: $foresporselId")
            try {
                authorize(
                    forespørselId = foresporselId,
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = cache
                )
                val transaksjonsId = kvitteringProducer.publish(foresporselId)
                val resultat = redis.hent(transaksjonsId, 10, 500)
                val dok = resultat.toString()
                sikkerlogg.info("Fikk resultat: $dok")
                if (dok == "{}") { // TODO .. litt smartere sjekk?
                    call.respond(HttpStatusCode.NotFound, "")
                } else {
                    val innsending = mapInnsending(customObjectMapper().readValue(dok, InntektsmeldingDokument::class.java))
                    call.respond(HttpStatusCode.OK, innsending)
                }
            } catch (e: ManglerAltinnRettigheterException) {
                call.respond(HttpStatusCode.Forbidden, "Du har ikke rettigheter for organisasjon.")
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $foresporselId")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $foresporselId")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(foresporselId))
            }
        }
    }
}
