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
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import java.util.UUID

class ForespørselLagretListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {
    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.requireKey(DataFelt.ORGNRUNDERENHET.str)
    }
    override fun onEvent(packet: JsonMessage) {
        val uuid: String = UUID.randomUUID().toString()
        val forespørselId = packet[Key.FORESPOERSEL_ID.str]
        if (forespørselId.isMissingOrNull() || forespørselId.asText().isEmpty()) {
            publishFail(packet.createFail("Mangler forespørselId"))
            return
        }
        publishBehov(
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.OPPRETT_OPPGAVE,
                    Key.FORESPOERSEL_ID.str to forespørselId,
                    Key.UUID.str to uuid,
                    DataFelt.ORGNRUNDERENHET.str to packet[DataFelt.ORGNRUNDERENHET.str]
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

    private val EVENT = EventName.FORESPØRSEL_LAGRET

    private fun opprettOppgave(
        forespørselId: String,
        orgnr: String
    ): String { // ktlint-disable trailing-comma-on-declaration-site
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(
                forespørselId,
                "$linkUrl/im-dialog/$forespørselId",
                "Send inn inntektsmelding",
                orgnr,
                "Inntektsmelding",
                null,
                forespørselId,
                "Nav trenger inntektsmelding", // TODO: Legg til bedriftsnavn og orgnummer
                "En av dine ansatte har sendt søknad for sykepenger og vi trenger inntektsmelding for å behandle " +
                    "søknaden. Logg inn på Min side – arbeidsgiver på nav.no." // TODO: Legg til bedriftsnavn og orgnummer
            )
        }
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EVENT.name)
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_OPPGAVE.name)
            it.interestedIn(DataFelt.ORGNRUNDERENHET.str)
        }

    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("OpprettOppgaveLøser mottok pakke: ${packet.toJson()}")
        val forespørselId = packet[Key.FORESPOERSEL_ID.str]
        if (forespørselId.isMissingOrNull() || forespørselId.asText().isEmpty()) {
            publishFail(packet.createFail("Mangler forespørselId"))
            return
        }
        val uuid = packet[Key.UUID.str].asText()
        val orgnr = packet[DataFelt.ORGNRUNDERENHET.str].asText()
        val oppgaveId = opprettOppgave(forespørselId.asText(), orgnr)
        val message = JsonMessage.newMessage(
            mapOf(
                Key.BEHOV.str to BehovType.PERSISTER_OPPGAVE_ID.name,
                Key.FORESPOERSEL_ID.str to forespørselId,
                Key.UUID.str to uuid,
                DataFelt.ORGNRUNDERENHET.str to orgnr,
                DataFelt.OPPGAVE_ID.str to oppgaveId
            )
        )
        publishBehov(message)
        sikkerLogger.info("OpprettOppgaveLøser publiserte med json: $message")
    }
}
