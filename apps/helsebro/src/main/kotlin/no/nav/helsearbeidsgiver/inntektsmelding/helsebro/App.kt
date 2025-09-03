package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.hag.simba.utils.felles.pritopic.Pri
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.river.ObjectRiver

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
