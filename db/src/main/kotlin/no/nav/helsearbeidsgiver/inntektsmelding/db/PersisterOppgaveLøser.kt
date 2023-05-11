package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser

class PersisterOppgaveLøser(
    rapidsConnection: RapidsConnection,
    val repository: ForespoerselRepository
) : Løser(rapidsConnection) {

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_OPPGAVE_ID.name)
        it.requireKey(DataFelt.OPPGAVE_ID.str)
    }
    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("PersisterOppgaveLøser mottok pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val oppgaveId = packet[DataFelt.OPPGAVE_ID.str].asText()
        repository.oppdaterOppgaveId(uuid, oppgaveId)
        publishEvent(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.OPPGAVE_LAGRET.name,
                    DataFelt.OPPGAVE_ID.str to oppgaveId
                )
            )
        )
        sikkerLogger.info("PersisterOppgaveLøser lagret oppgaveId $oppgaveId for uuid $uuid")
    }
}
