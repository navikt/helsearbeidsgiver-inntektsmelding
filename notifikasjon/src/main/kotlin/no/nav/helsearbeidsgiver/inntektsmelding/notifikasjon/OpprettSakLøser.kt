package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Lytt på event: EventName.OPPRETTE_SAK
 *
 * Opprett sak
 *
 * Publish: OppgaveSakEvent
 */
class OpprettSakLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val BEHOV = BehovType.OPPRETT_OPPGAVE

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireValue(Key.EVENT_NAME.str, EVENT.name)
            it.requireValue(Key.BEHOV.str, BehovType.FULLT_NAVN.name)
            it.requireKey(Key.LØSNING.str)
            it.requireKey(Key.UUID.str)
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
        }
    }

    fun opprettSak(
        uuid: String,
        orgnr: String,
        navn: String,
        fødselsdato: LocalDate
    ): String {
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                grupperingsid = uuid,
                merkelapp = "Inntektsmelding",
                virksomhetsnummer = orgnr,
                tittel = "Inntektsmelding for $navn: f. $fødselsdato",
                lenke = "$linkUrl/im-dialog/$uuid",
                harddeleteOm = "P5M"
            )
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Mottok event: $EVENT for uuid: $uuid")
        sikkerLogger.info("Mottok event $EVENT for uuid: $uuid, pakke: ${packet.toJson()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navn = packet[Key.LØSNING.str].toJsonElement().fromJson(NavnLøsning.serializer()).value ?: "Ukjent"
        val sakId = opprettSak(uuid, orgnr, navn, LocalDate.now())
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EVENT.name,
                Key.BEHOV.str to listOf(BehovType.PERSISTER_SAK_ID.name, BEHOV.name),
                Key.UUID.str to uuid,
                Key.IDENTITETSNUMMER.str to fnr,
                Key.ORGNRUNDERENHET.str to orgnr,
                Key.NAVN.str to navn,
                Key.SAK_ID.str to sakId
            )
        )
        publishBehov(message)
        sikkerLogger.info("Publiserte event: $EVENT med behov: $BEHOV for uuid: $uuid")
    }
}
