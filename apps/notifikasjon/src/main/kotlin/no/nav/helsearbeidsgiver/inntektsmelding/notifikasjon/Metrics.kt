package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.prometheus.client.Summary

object Metrics {

    val requestLatency = Summary.build()
        .name("simba_notifikasjon_latency_seconds")
        .help("notifikasjonklient kall mot fager latency in seconds")
        .labelNames("method")
        .register()
}
