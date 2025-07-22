package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection

fun RapidsConnection.onStartup(block: () -> Unit): RapidsConnection =
    also {
        it.register(
            object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    block()
                }
            },
        )
    }

fun RapidsConnection.onShutdown(block: () -> Unit): RapidsConnection =
    also {
        it.register(
            object : RapidsConnection.StatusListener {
                override fun onShutdown(rapidsConnection: RapidsConnection) {
                    block()
                }
            },
        )
    }
