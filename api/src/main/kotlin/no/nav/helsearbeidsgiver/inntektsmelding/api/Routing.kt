package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.RedisClient
import java.time.LocalDate
import no.nav.helsearbeidsgiver.inntektsmelding.api.mock.mockOrganisasjoner
import java.util.concurrent.TimeoutException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattPeriode

fun Application.configureRouting(producer: InntektsmeldingRegistrertProducer) {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/") {
            call.respondText("Hello inntektsmelding")
        }
        route("/api/v1") {
            route("/arbeidsgivere") {
                get {
                    call.respond(mockOrganisasjoner())
                }
            }
            route("/login-expiry") {
                get {
                    call.respond(HttpStatusCode.OK, "2099-05-31")
                }
            }
            route("/inntektsmelding") {
                post {
                    val request = call.receive<InntektsmeldingRequest>()
                    logger.info("Mottok inntektsmelding $request")
                    request.validate()
                    logger.info("Publiser til Rapid")
                    val uuid = producer.publish(request)
                    val redisUrl = "helsearbeidsgiver-redis.helsearbeidsgiver.svc.cluster.local"
                    val poller = RedisPoller(RedisClient.create("redis://$redisUrl:6379/0"))
                    try {
                        val value = poller.getValue(uuid, 5, 500)
                        call.respond(HttpStatusCode.Created, value)
                    } catch (ex: TimeoutException) {
                        call.respond(HttpStatusCode.InternalServerError, "Klarte ikke hente data")
                    }
                    poller.shutdown()
                }
            }
            route("/preutfyll") {
                post {
                    val request = call.receive<InntektsmeldingRequest>()
                    val response = Inntektsmelding(
                        navn = "Ola Normann",
                        identitetsnummer = request.identitetsnummer,
                        virksomhetsnavn = "Norge AS",
                        orgnrUnderenhet = request.orgnrUnderenhet,
                        fravaersperiode = listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))),
                        egenmeldingsperioder = listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))),
                        bruttoinntekt = 1000,
                        tidligereinntekt = listOf(MottattHistoriskInntekt("Januar", 1)),
                        behandlingsdager = listOf(LocalDate.of(2022, 1, 1)),
                        behandlingsperiode = MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2)),
                        arbeidsforhold = listOf(MottattArbeidsforhold("1", "test", 100.0f))
                    )
                    call.respond(HttpStatusCode.OK, Json.encodeToString(response))
                }
            }
        }
    }
    routing {
        get("isalive") {
            call.respondText("I'm alive")
        }
        get("isready") {
            call.respondText("I'm ready")
        }
    }
}
