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
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.slf4j.LoggerFactory

class OpprettOppgaveLøser(
    val rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val om = customObjectMapper()
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Starting OpprettOppgaveLøser...")
        River(rapidsConnection).apply {
            validate {
                it.requireValue(Key.EVENT_NAME.str, EVENT.name)
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
                uuid,
                "$linkUrl/im-dialog/$uuid",
                "Send inn inntektsmelding",
                orgnr,
                "Inntektsmelding",
                null,
                uuid
            )
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("OpprettOppgaveLøser mottok pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navn = packet[Key.NAVN.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        val oppgaveId = opprettOppgave(uuid, orgnr)
        val message = mapOf(
            Key.EVENT_NAME.str to EVENT,
            Key.BEHOV.str to listOf(BehovType.PERSISTER_OPPGAVE_ID.name),
            Key.UUID.str to uuid,
            Key.IDENTITETSNUMMER.str to fnr,
            Key.ORGNRUNDERENHET.str to orgnr,
            Key.NAVN.str to navn,
            Key.SAK_ID.str to sakId,
            Key.OPPGAVE_ID.str to oppgaveId
        )
        val json = om.writeValueAsString(message)
        rapidsConnection.publish(json)
        sikkerLogger.info("OpprettOppgaveLøser publiserte uuid $uuid med json: $json")
    }
}
