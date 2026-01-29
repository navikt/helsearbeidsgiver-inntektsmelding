package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import no.nav.hag.simba.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.river.ObjectRiver

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
