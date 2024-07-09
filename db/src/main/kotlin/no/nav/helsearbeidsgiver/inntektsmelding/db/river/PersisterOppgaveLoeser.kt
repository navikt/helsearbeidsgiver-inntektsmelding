package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishEvent
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class PersisterOppgaveLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository,
) : Loeser(rapidsConnection) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_OPPGAVE_ID.name)
            it.requireKey(Key.FORESPOERSEL_ID.str)
            it.requireKey(Key.OPPGAVE_ID.str)
        }

    override fun onBehov(behov: Behov) {
        sikkerLogger.info("PersisterOppgaveLøser mottok for uuid: ${behov.jsonMessage[Key.UUID.str].asText()}")

        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)
        val oppgaveId = behov[Key.OPPGAVE_ID].asText()

        repository.oppdaterOppgaveId(forespoerselId.toString(), oppgaveId)

        rapidsConnection.publishEvent(
            eventName = EventName.OPPGAVE_LAGRET,
            transaksjonId = null,
            forespoerselId = forespoerselId,
            Key.OPPGAVE_ID to oppgaveId.toJson(),
        )

        sikkerLogger.info("PersisterOppgaveLøser lagret oppgaveId $oppgaveId for forespoerselID ${behov.forespoerselId}")
    }
}
