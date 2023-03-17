package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import org.slf4j.LoggerFactory

/**
 * Input: FORESPØRSEL_MOTTATT
 *
 * - Hent FulltNavn
 * - Notifikasjon Sak
 * - Persister sak
 * - Notifikasjon Oppgave
 * - Persister oppgave
 *
 */
class ForespørselMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    override val event: EventName = EventName.FORESPØRSEL_MOTTATT

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.UUID.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Mottok event: $event for uuid: $uuid")
        sikkerLogger.info("Mottok event $event for uuid: $uuid, pakke: ${packet.toJson()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT,
                Key.BEHOV.str to listOf(BehovType.FULLT_NAVN.name),
                Key.UUID.str to uuid,
                Key.IDENTITETSNUMMER.str to fnr,
                Key.ORGNRUNDERENHET.str to orgnr
            )
        )
        publishBehov(message)
        sikkerLogger.info("Publiserte event: $event med behov: ${BehovType.FULLT_NAVN} for uuid: $uuid")
    }
}
