package no.nav.helsearbeidsgiver.felles.rr.river

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rr.KafkaKey
import no.nav.helsearbeidsgiver.felles.rr.Publisher

typealias MessageHandler = JsonElement.() -> Pair<KafkaKey, Map<Key, JsonElement>>?

// Trenger egen funksjon for å kunne mockes
internal fun createRapid(): RapidsConnection = RapidApplication.create(System.getenv())

internal fun createAndConnectToRapid(
    onStartup: () -> Unit = {},
    onShutdown: () -> Unit = {},
    rivers: (Publisher) -> List<MessageHandler>,
) {
    val rapid =
        createRapid()
            .onStartup(onStartup)
            .onShutdown(onShutdown)

    val publisher = Publisher(rapid)

    rivers(publisher).forEach {
        OpenRiver(rapid, it)
    }

    rapid.start()
}

private fun RapidsConnection.onStartup(block: () -> Unit): RapidsConnection =
    also {
        it.register(
            object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    block()
                }
            },
        )
    }

private fun RapidsConnection.onShutdown(block: () -> Unit): RapidsConnection =
    also {
        it.register(
            object : RapidsConnection.StatusListener {
                override fun onShutdown(rapidsConnection: RapidsConnection) {
                    block()
                }
            },
        )
    }
