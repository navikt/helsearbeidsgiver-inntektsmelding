package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class DataKanal(val rapid: RapidsConnection) : River.PacketListener {
    abstract val event: EventName

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    init {
        configure(
            River(rapid).apply {
                validate(accept())
            }
        ).register(this)
    }

    abstract fun accept(): River.PacketValidation

    private fun configure(river: River): River {
        return river.validate {
            it.demandValue(Key.EVENT_NAME.str, event.name)
            it.demandKey(Key.DATA.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet[Key.FORESPOERSEL_ID.str].asText().isEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }
        onData(packet)
    }

    abstract fun onData(packet: JsonMessage)
}
