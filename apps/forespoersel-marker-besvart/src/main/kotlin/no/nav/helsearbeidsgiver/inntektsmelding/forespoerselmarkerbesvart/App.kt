package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver

fun main() {
    val producer = Producer(Pri.TOPIC)

    ObjectRiver.connectToRapid(
        onShutdown = { producer.close() },
    ) {
        createForespoerselEventSwitch(producer)
    }
}

fun createForespoerselEventSwitch(producer: Producer): List<ObjectRiver<*, *>> =
    listOf(
        MarkerForespoerselBesvartRiver(producer),
        ForespoerselMottattRiver(),
        ForespoerselBesvartRiver(),
        ForespoerselKastetTilInfotrygdRiver(),
        ForespoerselForkastetRiver(),
    )
