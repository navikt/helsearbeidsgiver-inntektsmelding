package no.nav.helsearbeidsgiver.felles.metrics

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking

object Metrics {
    val altinnRequest = Summary.build()
        .name("simba_altinn_hent_rettighet_organisasjoner_latency_seconds")
        .help("Altinn hentRettighetOrganisasjoner - latency in seconds")
        .register()
}

fun <T> Summary.recordTime(block: suspend () -> T): T {
    val requestTimer = startTimer()
    return runBlocking { block() }
        .also {
            requestTimer.observeDuration()
        }
}
