package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
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
    val rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val om = customObjectMapper()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val BEHOV = BehovType.OPPRETT_OPPGAVE

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue(Key.EVENT_NAME.str, EventName.FORESPØRSEL_MOTTATT.name)
                it.demandAll(Key.BEHOV.str, BehovType.FULLT_NAVN)
                it.requireKey(Key.LØSNING.str)
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.ORGNRUNDERENHET.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
            }
        }.register(this)
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

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("OpprettSakLøser: fikk pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navn = packet[Key.LØSNING.str].toJsonElement().fromJson(NavnLøsning.serializer()).value ?: "Ukjent"
        // val sakId = opprettSak(uuid, orgnr, navn, LocalDate.now())
        val sakId = "123"
        val msg =
            mapOf(
                Key.EVENT_NAME.str to EVENT.name,
                Key.BEHOV.str to listOf(BehovType.PERSISTER_SAK_ID.name, BehovType.OPPRETT_OPPGAVE.name),
                Key.UUID.str to uuid,
                Key.IDENTITETSNUMMER.str to fnr,
                Key.ORGNRUNDERENHET.str to orgnr,
                Key.NAVN.str to navn,
                Key.SAK_ID.str to sakId
            )

        val json = om.writeValueAsString(msg)
        rapidsConnection.publish(json)
        logger.info("OpprettSakLøser: Publiserte: $json")
    }
}
