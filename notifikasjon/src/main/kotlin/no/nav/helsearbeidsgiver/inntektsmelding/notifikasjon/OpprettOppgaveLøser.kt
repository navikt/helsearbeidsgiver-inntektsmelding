package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterSakIdLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.slf4j.LoggerFactory

/**
 * Opprett oppgave i en sak
 */
class OpprettOppgaveLøser(
    val rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val om = customObjectMapper()

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val BEHOV = BehovType.FULLT_NAVN

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue(Key.EVENT_NAME.str, EventName.FORESPØRSEL_MOTTATT.name)
                it.demandAll(Key.BEHOV.str, listOf(BehovType.OPPRETT_OPPGAVE.name))
                it.requireKey(Key.UUID.str)
                it.interestedIn(Key.IDENTITETSNUMMER.str)
                it.interestedIn(Key.ORGNRUNDERENHET.str)
                it.interestedIn(Key.NAVN.str)
                it.interestedIn(Key.SAK_ID.str)
                it.interestedIn(Key.OPPGAVE_ID.str)
            }
        }.register(this)
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

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("OpprettOppgaveLøser: Mottok ${packet.toJson()}")
        sikkerLogger.info("Mottok event $EVENT, pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navn = packet[Key.NAVN.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        //val oppgaveId = opprettOppgave(uuid, orgnr)
        val oppgaveId = 12
        val message = mapOf(
            Key.EVENT_NAME.str to listOf(EVENT),
            Key.BEHOV.str to listOf(BehovType.PERSISTER_OPPGAVE_ID.name),
            Key.UUID.str to uuid,
            Key.IDENTITETSNUMMER.str to fnr,
            Key.ORGNRUNDERENHET.str to orgnr,
            Key.NAVN.str to navn,
            Key.SAK_ID.str to sakId,
            Key.OPPGAVE_ID.str to oppgaveId
        )

        //publiserLøsning(message)
        rapidsConnection.publish(om.writeValueAsString(message))
        logger.info("OpprettOppgaveLøser: Publiserte event: $EVENT med behov: $BEHOV for uuid: $uuid")
    }

    fun publiserLøsning(løsning: PersisterSakIdLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
