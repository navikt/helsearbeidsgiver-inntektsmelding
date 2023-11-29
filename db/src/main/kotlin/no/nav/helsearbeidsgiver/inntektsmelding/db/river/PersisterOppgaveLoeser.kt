package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterOppgaveLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository
) : Loeser(rapidsConnection) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_OPPGAVE_ID.name)
        it.requireKey(Key.FORESPOERSEL_ID.str)
        it.requireKey(Key.OPPGAVE_ID.str)
    }

    override fun onBehov(behov: Behov) {
        sikkerLogger.info("PersisterOppgaveLøser mottok for uuid: ${behov.uuid()}")
        val oppgaveId = behov[Key.OPPGAVE_ID].asText()
        repository.oppdaterOppgaveId(behov.forespoerselId!!, oppgaveId)
        behov.createEvent(EventName.OPPGAVE_LAGRET, mapOf(Key.OPPGAVE_ID to oppgaveId)).also { publishEvent(it) }
        sikkerLogger.info("PersisterOppgaveLøser lagret oppgaveId $oppgaveId for forespoerselID ${behov.forespoerselId}")
    }
}
