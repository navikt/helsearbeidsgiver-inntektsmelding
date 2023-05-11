package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpprettSakLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_SAK.name)
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.interestedIn(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
        }
    }

    fun opprettSak(
        forespoerselId: String,
        orgnr: String,
        navn: String,
        fødselsdato: LocalDate?
    ): String {
        val datoString = fødselsdato?.format(DateTimeFormatter.ofPattern("ddMMyy")) ?: "Ukjent"
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                grupperingsid = forespoerselId,
                merkelapp = "Inntektsmelding",
                virksomhetsnummer = orgnr,
                tittel = "Inntektsmelding for $navn: f. $datoString",
                lenke = "$linkUrl/im-dialog/$forespoerselId",
                statusTekst = "NAV trenger inntektsmelding",
                harddeleteOm = "P5M"
            )
        }
    }

    fun hentNavn(packet: JsonMessage): PersonDato {
        if (packet[DataFelt.ARBEIDSTAKER_INFORMASJON.str].isMissingNode) return PersonDato("Ukjent", null)
        return customObjectMapper().treeToValue(packet[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java)
    }

    override fun onBehov(packet: JsonMessage) {
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        sikkerLogger.info("OpprettSakLøser: fikk pakke: ${packet.toJson()}")
        logger.info("Skal opprette sak for forespørselId: $forespoerselId")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val personDato = hentNavn(packet)
        val navn = personDato.navn
        val fødselsdato = personDato.fødselsdato
        val sakId = opprettSak(forespoerselId, orgnr, navn, fødselsdato)
        logger.info("OpprettSakLøser fikk opprettet sak for forespørselId: $forespoerselId")
        publishData(
            JsonMessage.newMessage(
                mapOf(
                    Key.DATA.str to "",
                    Key.FORESPOERSEL_ID.str to forespoerselId,
                    Key.UUID.str to packet[Key.UUID.str],
                    DataFelt.SAK_ID.str to sakId
                )
            )
        )
        sikkerLogger.info("OpprettSakLøser publiserte med sakId=$sakId")
    }
}
