package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.PluginInstance
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import no.nav.helsearbeidsgiver.felles.json.configure

fun Application.contentNegotiation(): PluginInstance =
    install(ContentNegotiation) {
        jackson {
            configure()
        }
    }

suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.respondOk(message: T) {
    call.respond(HttpStatusCode.OK, message)
}

suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.respondInternalServerError(message: T) {
    call.respond(HttpStatusCode.InternalServerError, message)
}
