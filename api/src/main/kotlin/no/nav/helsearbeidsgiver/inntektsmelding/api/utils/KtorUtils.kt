package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.PluginInstance
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import no.nav.helsearbeidsgiver.felles.json.configure

fun Application.contentNegotiation(): PluginInstance =
    install(ContentNegotiation) {
        jackson {
            configure()
        }
    }
