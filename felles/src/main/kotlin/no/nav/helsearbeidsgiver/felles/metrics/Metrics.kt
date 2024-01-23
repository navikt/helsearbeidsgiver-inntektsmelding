package no.nav.helsearbeidsgiver.felles.metrics

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking

object Metrics {
    val dbAapenIm: Summary = Summary.build()
        .name("simba_db_aapen_im_repo_latency_seconds")
        .help("Latency (i sek.) for database 'im-db' and table 'aapen_inntektsmelding'.")
        .labelNames("method")
        .register()

    val altinnRequest: Summary = Summary.build()
        .name("simba_altinn_hent_rettighet_organisasjoner_latency_seconds")
        .help("Latency (i sek.) for Altinn-hentRettighetOrganisasjoner.")
        .register()
}

/** Bruk av [label] krever at `labelNames` er satt på [Summary]. */
fun <T> Summary.recordTime(label: String? = null, block: suspend () -> T): T {
    val requestTimer: Summary.Timer =
        if (label == null) {
            startTimer()
        } else {
            labels(label).startTimer()
        }

    return runBlocking { block() }
        .also {
            requestTimer.observeDuration()
        }
}
