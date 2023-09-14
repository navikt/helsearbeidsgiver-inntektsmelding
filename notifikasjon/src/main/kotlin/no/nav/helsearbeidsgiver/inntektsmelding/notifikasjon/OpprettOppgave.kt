package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event

class ForespørselLagretListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {
    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.requireKey(DataFelt.ORGNRUNDERENHET.str)

        // Vi skal manuelt publisere events som har gitt feil, men ønsker ikke at
        // denne løseren skal agere på eventsene, derfor filtrerer vi dem ut her midlertidig
        it.rejectValues(
            Key.FORESPOERSEL_ID.str,
            listOf(
                "2d62ac7f-fa2e-4770-9377-9eea1a94506f",
                "d6d61e86-1ea3-4250-b7b9-c1dd1767ef23",
                "81f7e7ab-9388-4f1a-b828-f5cb6e4a642a",
                "3bb011ed-b275-4df8-aec5-5b9c6ee60d95",
                "9ac8eb21-1e9c-4bd8-95b3-c2e3613ad82e"
            )
        )
    }

    override fun onEvent(event: Event) {
        if (event.forespoerselId.isNullOrBlank()) {
            publishFail(event.createFail("Mangler forespørselId"))
            return
        }

        event.createBehov(
            BehovType.OPPRETT_OPPGAVE,
            mapOf(
                DataFelt.ORGNRUNDERENHET to event[DataFelt.ORGNRUNDERENHET]
            )
        ).also { publishBehov(it) }
    }

    override fun onEvent(packet: JsonMessage) {
    }
}

class OpprettOppgaveLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val EVENT = EventName.FORESPØRSEL_LAGRET

    private fun opprettOppgave(
        forespørselId: String,
        orgnr: String
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
                varslingInnhold = "En av dine ansatte har sendt søknad for sykepenger og vi trenger inntektsmelding for å behandle " +
                    "søknaden. Logg inn på Min side – arbeidsgiver på nav.no"
            )
        }.also {
            requestTimer.observeDuration()
        }
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EVENT.name)
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_OPPGAVE.name)
            it.interestedIn(DataFelt.ORGNRUNDERENHET.str)
        }

    override fun onBehov(behov: Behov) {
        val forespoerselId = behov.forespoerselId
        if (forespoerselId.isNullOrBlank()) {
            publishFail(behov.createFail("Mangler forespørselId"))
            return
        }
        val oppgaveId = opprettOppgave(forespoerselId, behov[DataFelt.ORGNRUNDERENHET].asText())
        behov.createBehov(
            BehovType.PERSISTER_OPPGAVE_ID,
            mapOf(
                DataFelt.ORGNRUNDERENHET to behov[DataFelt.ORGNRUNDERENHET].asText(),
                DataFelt.OPPGAVE_ID to oppgaveId
            )
        ).also { publishBehov(it) }

        sikkerLogger.info("OpprettOppgaveLøser publiserte med uuid: ${behov.uuid()}")
    }
}
