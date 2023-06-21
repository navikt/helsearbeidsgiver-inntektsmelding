package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class EventListener(val rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val event: EventName
    // state! :/
//    lateinit var forespørselId: String

    init {
        configureAsListener(
            River(rapidsConnection).apply {
                validate(accept())
            }
        ).register(this)
    }

    abstract fun accept(): River.PacketValidation

    private fun configureAsListener(river: River): River {
        return river.validate {
            it.rejectKey(Key.BEHOV.str)
            it.demandValue(Key.EVENT_NAME.str, event.name)
            it.rejectKey(Key.LØSNING.str)
            it.rejectKey(Key.DATA.str)
            it.rejectKey(Key.FAIL.str)
            it.interestedIn(Key.CLIENT_ID.str)
            // transaksjon kan ikke ha UUID / transaksjonsID som egentlig betyr noe, men jeg beholder det for øyebliket
            // for backward compatability
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
            it.interestedIn(Key.TRANSACTION_ORIGIN.str)
        }
    }
    fun publishBehov(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = event.name

        rapidsConnection.publish(message.toJson())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet[Key.FORESPOERSEL_ID.str].asText().isNullOrEmpty()) {
            sikkerLogger.warn("Mangler forespørselId for event: ${event.name} !")
            sikkerLogger.warn("Packet = ${packet.toJson()}")
        }
        onEvent(packet)
    }

    abstract fun onEvent(packet: JsonMessage)

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail.toJsonMessage().toJson())
    }
}
