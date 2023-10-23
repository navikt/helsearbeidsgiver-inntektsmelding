package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov

class OpprettOppgaveLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Loeser(rapidsConnection) {

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_OPPGAVE.name)
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.requireKey(DataFelt.VIRKSOMHET.str)
        }

    override fun onBehov(behov: Behov) {
        val forespoerselId = behov.forespoerselId
        if (forespoerselId.isNullOrBlank()) {
            publishFail(behov.createFail("Mangler forespørselId"))
            return
        }
        val oppgaveId = opprettOppgave(
            forespoerselId,
            behov[DataFelt.ORGNRUNDERENHET].asText(),
            behov[DataFelt.VIRKSOMHET].asText()
        )

        behov.createBehov(
            BehovType.PERSISTER_OPPGAVE_ID,
            mapOf(
                DataFelt.ORGNRUNDERENHET to behov[DataFelt.ORGNRUNDERENHET].asText(),
                DataFelt.OPPGAVE_ID to oppgaveId
            )
        ).also { publishBehov(it) }

        sikkerLogger.info("OpprettOppgaveLøser publiserte med uuid: ${behov.uuid()}")
    }

    private fun opprettOppgave(
        forespørselId: String,
        orgnr: String,
        virksomhetnavn: String
    ): String {
        val requestTimer = Metrics.requestLatency.labels("opprettOppgave").startTimer()
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(
                eksternId = forespørselId,
                lenke = "$linkUrl/im-dialog/$forespørselId",
                tekst = "Send inn inntektsmelding",
                virksomhetsnummer = orgnr,
                merkelapp = "Inntektsmelding",
                tidspunkt = null,
                grupperingsid = forespørselId,
                varslingTittel = "Nav trenger inntektsmelding",
                varslingInnhold = """$virksomhetnavn - orgnr $orgnr: En av dine ansatte har søkt om sykepenger
                    og vi trenger inntektsmelding for å behandle søknaden.
                    Logg inn på Min side – arbeidsgiver hos NAV.
                    Hvis dere sender inntektsmelding via lønnssystem kan dere fortsatt gjøre dette,
                    og trenger ikke sende inn via Min side – arbeidsgiver."""
            )
        }.also {
            requestTimer.observeDuration()
        }
    }
}
