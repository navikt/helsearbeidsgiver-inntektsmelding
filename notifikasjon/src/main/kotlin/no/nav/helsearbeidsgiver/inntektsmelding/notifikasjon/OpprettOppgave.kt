package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event

class ForespoerselLagretListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {
    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.requireKey(DataFelt.ORGNRUNDERENHET.str)
    }

    override fun onEvent(packet: JsonMessage) {
        val event = createEvent(packet)
        if (event.forespoerselId.isNullOrBlank()) {
            publishFail(event.createFail("Mangler forespørselId"))
            return
        }

        event.createBehov(
            BehovType.OPPRETT_OPPGAVE,
            mapOf(
                DataFelt.ORGNRUNDERENHET to event.jsonMessage[DataFelt.ORGNRUNDERENHET.str]
            )
        ).also { publishBehov(it) }
    }

    private fun createEvent(jsonMessage: JsonMessage): Event {
        val event = EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText())
        val clientID = jsonMessage[Key.CLIENT_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
        val forespoerselId = jsonMessage[Key.FORESPOERSEL_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
        return Event(event, forespoerselId, jsonMessage, clientID)
    }
}

class OpprettOppgaveLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Loeser(rapidsConnection) {

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
                    "søknaden. Logg inn på Min side – arbeidsgiver hos NAV"
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
        val oppgaveId = opprettOppgave(forespoerselId, behov.jsonMessage[DataFelt.ORGNRUNDERENHET.str].asText())
        behov.createBehov(
            BehovType.PERSISTER_OPPGAVE_ID,
            mapOf(
                DataFelt.ORGNRUNDERENHET to behov.jsonMessage[DataFelt.ORGNRUNDERENHET.str].asText(),
                DataFelt.OPPGAVE_ID to oppgaveId
            )
        ).also { publishBehov(it) }

        sikkerLogger.info("OpprettOppgaveLøser publiserte med uuid: ${behov.uuid()}")
    }
}
