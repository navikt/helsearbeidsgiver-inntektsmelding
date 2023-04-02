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

class OpprettSakLøser(
    val rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val om = customObjectMapper()
    private val EVENT = EventName.FORESPØRSEL_MOTTATT
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Starting OpprettSakLøser...")
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
                statusTekst = "NAV trenger inntektsmelding",
                harddeleteOm = "P5M"
            )
        }
    }

    fun hentNavn(packet: JsonMessage): NavnLøsning {
        return packet[Key.LØSNING.str].get(BehovType.FULLT_NAVN.name).toJsonElement().fromJson(NavnLøsning.serializer())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        sikkerLogger.info("OpprettSakLøser: fikk pakke: ${packet.toJson()}")
        logger.info("Skal opprette sak for forespørselId: $uuid")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val navnLøsning = hentNavn(packet)
        val navn = hentNavn(packet).value ?: "Ukjent"
        if (navnLøsning.error != null) {
            logger.warn("Klarte ikke hente navn for forespørselId: $uuid ved oppretting av sak!")
            sikkerLogger.warn(
                "Fikk feilmelding ved henting av navn (org: $orgnr, fnr: $fnr) for forespørselId: $uuid. Feilmelding: ${navnLøsning.error?.melding}"
            )
        }
        val fødselsdato = LocalDate.now()
        val sakId = opprettSak(uuid, orgnr, navn, fødselsdato)
        logger.info("OpprettSakLøser fikk opprettet sak for forespørselId: $uuid")
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
        sikkerLogger.info("OpprettSakLøser: Publiserte: $json")
        logger.info("OpprettSakLøser publiserte behov ${BehovType.PERSISTER_SAK_ID.name} og ${BehovType.OPPRETT_OPPGAVE.name} forespørselId: $uuid")
    }
}
