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
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory
import java.util.UUID

class ForespørselMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {
    override val event: EventName = EventName.FORESPØRSEL_MOTTATT

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.requireKey(Key.ORGNRUNDERENHET.str)
    }
    override fun onEvent(packet: JsonMessage) {
        val uuid: String = UUID.randomUUID().toString()
        publishBehov(
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.OPPRETT_OPPGAVE,
                    Key.UUID.str to uuid,
                    Key.ORGNRUNDERENHET.str to packet[Key.ORGNRUNDERENHET.str]
                )
            )
        )
    }
}

class OpprettOppgaveLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val om = customObjectMapper()
    private val EVENT = EventName.FORESPØRSEL_LAGRET
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun opprettOppgave(
        uuid: String,
        orgnr: String
    ): String { // ktlint-disable trailing-comma-on-declaration-site
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(
                uuid,
                "$linkUrl/im-dialog/$uuid",
                "Send inn inntektsmelding",
                orgnr,
                "Inntektsmelding",
                null,
                uuid,
                "Nav trenger inntektsmelding",
                "En av dine ansatte har sendt søknad for sykepenger og vi trenger inntektsmelding for å behandle " +
                    "søknaden. Logg inn på Min side – arbeidsgiver på nav.no"
            )
        }
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.requireValue(Key.EVENT_NAME.str, EVENT.name)
            it.demandAll(Key.BEHOV.str, listOf(BehovType.OPPRETT_OPPGAVE.name))
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.ORGNRUNDERENHET.str)
        }

    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("OpprettOppgaveLøser mottok pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val oppgaveId = opprettOppgave(uuid, orgnr)
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EVENT,
                Key.BEHOV.str to listOf(BehovType.PERSISTER_OPPGAVE_ID.name),
                Key.UUID.str to uuid,
                Key.ORGNRUNDERENHET.str to orgnr,
                Key.OPPGAVE_ID.str to oppgaveId
            )
        )
        publishData(message)
        sikkerLogger.info("OpprettOppgaveLøser publiserte uuid $uuid med json: $message")
    }
}
