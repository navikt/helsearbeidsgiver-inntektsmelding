package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.RapidsConnection

fun RapidsConnection.registerShutdownLifecycle(onShutdown: () -> Unit): RapidsConnection =
    also {
        it.register(
            object : RapidsConnection.StatusListener {
                override fun onShutdown(rapidsConnection: RapidsConnection) {
                    onShutdown()
                }
            },
        )
    }
