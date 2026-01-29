package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.river.ObjectRiver

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
