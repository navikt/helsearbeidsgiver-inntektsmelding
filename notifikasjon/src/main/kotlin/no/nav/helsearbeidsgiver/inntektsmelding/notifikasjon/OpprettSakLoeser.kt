package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNySakException
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val birthDateFormatter = DateTimeFormatter.ofPattern("ddMMyy")

class OpprettSakLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Loeser(rapidsConnection) {

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
        foedselsdato: LocalDate?
    ): String? {
        val formattertFoedselsdato = foedselsdato?.format(birthDateFormatter) ?: "Ukjent"
        val requestTimer = Metrics.requestLatency.labels("opprettSak").startTimer()
        return try {
            runBlocking {
                arbeidsgiverNotifikasjonKlient.opprettNySak(
                    grupperingsid = forespoerselId,
                    merkelapp = "Inntektsmelding",
                    virksomhetsnummer = orgnr,
                    tittel = "Inntektsmelding for $navn: f. $formattertFoedselsdato",
                    lenke = "$linkUrl/im-dialog/$forespoerselId",
                    statusTekst = "NAV trenger inntektsmelding",
                    harddeleteOm = "P5M"
                )
            }.also {
                requestTimer.observeDuration()
            }
        } catch (e: OpprettNySakException) {
            sikkerLogger.error("Feil ved kall til opprett oppgave for $forespoerselId!", e)
            logger.error("Feil ved kall til opprett oppgave for $forespoerselId!")
            return null
        }
    }

    private fun hentNavn(behov: Behov): PersonDato {
        if (behov[DataFelt.ARBEIDSTAKER_INFORMASJON].isMissingNode) return PersonDato("Ukjent", null, "")
        return behov[DataFelt.ARBEIDSTAKER_INFORMASJON].toJsonElement().fromJson(PersonDato.serializer())
    }

    override fun onBehov(behov: Behov) {
        logger.info("Skal opprette sak for forespørselId: ${behov.forespoerselId}")
        val orgnr = behov[DataFelt.ORGNRUNDERENHET].asText()
        val personDato = hentNavn(behov)
        val sakId = opprettSak(
            forespoerselId = behov.forespoerselId!!,
            orgnr = orgnr,
            navn = personDato.navn,
            foedselsdato = personDato.fødselsdato
        )
        if (sakId.isNullOrBlank()) {
            val feil = Fail(behov.event, behov.behov, "Opprett sak feilet", null, behov.uuid(), behov.forespoerselId)
            rapidsConnection.publish(feil.toJsonMessage().toJson()) // TODO: Fiks feilhåndtering
        } else {
            logger.info("OpprettSakLøser fikk opprettet sak for forespørselId: ${behov.forespoerselId}")
            behov.createData(mapOf(DataFelt.SAK_ID to sakId)).also { publishData(it) }
            sikkerLogger.info("OpprettSakLøser publiserte med sakId=$sakId og forespoerselId=${behov.forespoerselId}")
        }
    }
}
