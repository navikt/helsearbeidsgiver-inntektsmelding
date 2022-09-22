package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattPeriode
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.PreutfyltResponse
import java.time.LocalDate

fun Route.Preutfylling(producer: PreutfyltProducer, redisUrl: String) {
    route("/preutfyll") {
        post {
            val request = call.receive<PreutfyllRequest>()
            request.validate()
            val response = PreutfyltResponse(
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
            producer.publish(request)
        }
    }
}
