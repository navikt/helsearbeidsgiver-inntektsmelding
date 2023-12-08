package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class FeilLytter(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val sikkerLogger = sikkerLogger()

    init {
        sikkerLogger.info("Starter applikasjon - lytter på innkommende feil!")
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demandKey(Key.FAIL.str)
            }
        }
            .register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("Mottok feil: ${packet.toPretty()}")
    }
}
