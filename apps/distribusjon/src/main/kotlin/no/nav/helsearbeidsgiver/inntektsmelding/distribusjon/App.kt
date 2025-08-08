package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver

fun main() {
    val producer = Producer("helsearbeidsgiver.inntektsmelding")

    ObjectRiver.connectToRapid(
        onShutdown = { producer.close() },
    ) {
        createDistribusjonRiver(producer)
    }
}

fun createDistribusjonRiver(producer: Producer): List<DistribusjonRiver> =
    listOf(
        DistribusjonRiver(producer),
    )
