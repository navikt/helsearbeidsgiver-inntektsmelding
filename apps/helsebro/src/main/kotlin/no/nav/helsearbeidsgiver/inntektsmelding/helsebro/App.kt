package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver

fun main() {
    val producer = Producer(Pri.TOPIC)

    ObjectRiver.connectToRapid(
        onShutdown = { producer.close() },
    ) {
        createHelsebroRivers(producer)
    }
}

fun createHelsebroRivers(producer: Producer): List<ObjectRiver<*, *>> =
    listOf(
        TrengerForespoerselRiver(producer),
        ForespoerselSvarRiver(),
        HentForespoerslerForVedtaksperiodeIdListeRiver(producer),
        VedtaksperiodeIdForespoerselSvarRiver(),
    )
