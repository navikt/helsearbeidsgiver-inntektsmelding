package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

/**
 * Opprett oppgave i en sak
 */
class OpprettOppgaveLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val BEHOV = BehovType.FULLT_NAVN

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireValue(Key.EVENT_NAME.str, EventName.FORESPØRSEL_MOTTATT.name)
            it.requireValue(Key.BEHOV.str, BehovType.OPPRETT_OPPGAVE.name)
            // it.requireKey(Key.LØSNING.str)
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.IDENTITETSNUMMER.str)
            it.interestedIn(Key.ORGNRUNDERENHET.str)
            it.interestedIn(Key.NAVN.str)
            it.interestedIn(Key.SAK_ID.str)
            it.interestedIn(Key.OPPGAVE_ID.str)
        }
    }

    fun opprettOppgave(
        uuid: String,
        orgnr: String
    ): String {
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(
                "eksternid",
                "$linkUrl/im-dialog/$uuid",
                "Oppgave venter",
                orgnr,
                "Inntektsmelding",
                null
            )
        }
    }

    override fun onBehov(packet: JsonMessage) {
        logger.info("Mottok løsning for $EVENT event med behov: $BEHOV")
        sikkerLogger.info("Mottok event $EVENT, pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navn = packet[Key.NAVN.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        val oppgaveId = opprettOppgave(uuid, orgnr)
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to listOf(EVENT),
                Key.BEHOV.str to listOf(BehovType.PERSISTER_OPPGAVE_ID.name),
                Key.UUID.str to uuid,
                Key.IDENTITETSNUMMER.str to fnr,
                Key.ORGNRUNDERENHET.str to orgnr,
                Key.NAVN.str to navn,
                Key.SAK_ID.str to sakId,
                Key.OPPGAVE_ID.str to oppgaveId
            )
        )
        publishBehov(message)
        sikkerLogger.info("Publiserte event: $EVENT med behov: $BEHOV for uuid: $uuid")
    }
}
