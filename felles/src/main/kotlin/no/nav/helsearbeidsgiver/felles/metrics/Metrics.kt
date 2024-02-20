package no.nav.helsearbeidsgiver.felles.metrics

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking

object Metrics {
    val dbAapenIm: Summary = Summary.build()
        .name("simba_db_aapen_im_repo_latency_seconds")
        .help("Latency (i sek.) for database 'im-db' and table 'aapen_inntektsmelding'.")
        .labelNames("method")
        .register()

    val dbAapenSak: Summary = Summary.build()
        .name("simba_db_selvbestemt_sak_repo_latency_seconds")
        .help("Latency (i sek.) for database 'im-notifikasjon' and table 'selvbestemt_sak'.")
        .labelNames("method")
        .register()

    val altinnRequest: Summary = Summary.build()
        .name("simba_altinn_hent_rettighet_organisasjoner_latency_seconds")
        .help("Latency (i sek.) for Altinn-hentRettighetOrganisasjoner.")
        .register()

    val agNotifikasjonRequest: Summary = Summary.build()
        .name("simba_agnotifikasjon_latency_seconds")
        .help("Latency (i sek.) for Fager-arbeidsgivernotifikasjonsklient.")
        .labelNames("method")
        .register()

    val dokArkivRequest: Summary = Summary.build()
        .name("simba_dokarkiv_latency_seconds")
        .help("Latency (i sek.) for dokarkiv-klient.")
        .labelNames("method")
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
