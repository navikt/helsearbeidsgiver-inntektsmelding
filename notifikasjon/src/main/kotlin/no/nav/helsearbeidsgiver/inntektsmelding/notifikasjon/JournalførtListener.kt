package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.slf4j.LoggerFactory

class JournalførtListener(val rapidsConnection: RapidsConnection) : River.PacketListener {

    private val om = customObjectMapper()
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Starting JournalførtListener...")
        River(rapidsConnection).apply {
            validate {
                it.requireValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_JOURNALFOERT.name)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.JOURNALPOST_ID.str)
                it.requireKey(Key.OPPGAVE_ID.str)
                it.requireKey(Key.SAK_ID.str)
                it.rejectKey(Key.BEHOV.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        val oppgaveId = packet[Key.OPPGAVE_ID.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        logger.info("JournalførtListener fikk pakke for $uuid")
        sikkerLogger.info("JournalførtListener fikk pakke ${EventName.INNTEKTSMELDING_JOURNALFOERT} med pakke ${packet.toJson()}")
        listOf(BehovType.DISTRIBUER_IM, BehovType.ENDRE_SAK_STATUS, BehovType.ENDRE_OPPGAVE_STATUS).forEach {
            publish(it, uuid, oppgaveId, sakId, packet)
        }
        logger.info("JournalførtListener fikk ferdigbehandlet $uuid.")
    }

    fun publish(behovType: BehovType, uuid: String, oppgaveId: String, sakId: String, packet: JsonMessage) {
        val json = om.writeValueAsString(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT.name,
                Key.BEHOV.str to listOf(behovType),
                Key.JOURNALPOST_ID.str to packet[Key.JOURNALPOST_ID.str].asText(),
                Key.UUID.str to uuid,
                Key.OPPGAVE_ID.str to oppgaveId,
                Key.SAK_ID.str to sakId,
                Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
            )
        )
        rapidsConnection.publish(json)
    }
}
