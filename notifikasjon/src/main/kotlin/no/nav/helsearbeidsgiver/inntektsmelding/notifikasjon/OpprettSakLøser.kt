package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpprettSakLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val logger = logger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_SAK.name)
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.interestedIn(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
        }
    }

    private fun opprettSak(
        forespoerselId: String,
        orgnr: String,
        navn: String,
        fødselsdato: LocalDate?
    ): String {
        val datoString = fødselsdato?.format(DateTimeFormatter.ofPattern("ddMMyy")) ?: "Ukjent"
        val requestTimer = Metrics.requestLatency.labels("opprettSak").startTimer()
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
        }.also {
            requestTimer.observeDuration()
        }
    }

    private fun hentNavn(behov: Behov): PersonDato {
        if (behov[DataFelt.ARBEIDSTAKER_INFORMASJON].isMissingNode) return PersonDato("Ukjent", null)
        return customObjectMapper().treeToValue(behov[DataFelt.ARBEIDSTAKER_INFORMASJON], PersonDato::class.java)
    }

    override fun onBehov(behov: Behov) {
        logger.info("Skal opprette sak for forespørselId: $forespoerselId")
        val orgnr = behov[DataFelt.ORGNRUNDERENHET].asText()
        val personDato = hentNavn(behov)
        val navn = personDato.navn
        val fødselsdato = personDato.fødselsdato
        val sakId = opprettSak(forespoerselId, orgnr, navn, fødselsdato)
        logger.info("OpprettSakLøser fikk opprettet sak for forespørselId: $forespoerselId")
        behov.createData(mapOf(DataFelt.SAK_ID to sakId)).also { publishData(it) }
        sikkerLogger.info("OpprettSakLøser publiserte med sakId=$sakId og forespoerselId=$forespoerselId")
    }

    override fun onBehov(packet: JsonMessage) {
    }
}
