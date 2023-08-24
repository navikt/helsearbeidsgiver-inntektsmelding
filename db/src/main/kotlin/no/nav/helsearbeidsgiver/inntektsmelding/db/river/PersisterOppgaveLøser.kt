package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterOppgaveLøser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository
) : Løser(rapidsConnection) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_OPPGAVE_ID.name)
        it.requireKey(Key.FORESPOERSEL_ID.str)
        it.requireKey(DataFelt.OPPGAVE_ID.str)
    }

    override fun onBehov(behov: Behov) {
        sikkerLogger.info("PersisterOppgaveLøser mottok for uuid: ${behov.uuid()}")
        val oppgaveId = behov[DataFelt.OPPGAVE_ID].asText()
        repository.oppdaterOppgaveId(behov.forespoerselId!!, oppgaveId)
        publishEvent(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.OPPGAVE_LAGRET.name,
                    DataFelt.OPPGAVE_ID.str to oppgaveId
                )
            )
        )
        sikkerLogger.info("PersisterOppgaveLøser lagret oppgaveId $oppgaveId for forespoerselID $forespoerselId")
    }

    override fun onBehov(packet: JsonMessage) {

    }
}
