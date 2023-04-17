package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import com.fasterxml.jackson.databind.JsonMappingException
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.fjernLedendeSlash
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

private const val EMPTY_PAYLOAD = "{}"

fun RouteExtra.KvitteringRoute(cache: LocalCache<Tilgang>) {
    val kvitteringProducer = KvitteringProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.KVITTERING) {
        get {
            val foresporselId = fjernLedendeSlash(call.parameters["uuid"].orEmpty())
            if (foresporselId.isEmpty() || foresporselId.length != 36) {
                logger.warn("Ugyldig parameter: $foresporselId")
                call.respond(HttpStatusCode.BadRequest)
            }
            logger.info("Henter data for uuid: $foresporselId")
            try {
                authorize(
                    forespørselId = foresporselId,
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = cache
                )
                val transaksjonsId = kvitteringProducer.publish(foresporselId)
                val dok = redis.getString(transaksjonsId, 10, 500)
                sikkerlogg.info("Forespørsel $foresporselId ga resultat: $dok")
                if (dok == EMPTY_PAYLOAD) {
                    // kvitteringService svarer med "{}" hvis det ikke er noen kvittering
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
            } catch (e: JsonMappingException) {
                logger.error("Kunne ikke parse json-resultat for $foresporselId")
                call.respond(HttpStatusCode.InternalServerError)
            } catch (_: RedisPollerTimeoutException) {
                logger.error("Fikk timeout for $foresporselId")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(foresporselId))
            }
        }
    }
}
