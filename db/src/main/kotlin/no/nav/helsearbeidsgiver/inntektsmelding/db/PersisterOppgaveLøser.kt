package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

class PersisterOppgaveLøser(
    rapidsConnection: RapidsConnection,
    val repository: Repository
) : Løser(rapidsConnection) {

    private val BEHOV = BehovType.PERSISTER_OPPGAVE_ID
    private val sikkerLogger = LoggerFactory.getLogger("tjenestekall")

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAny(Key.BEHOV.str, listOf(BEHOV.name))
            it.rejectKey(Key.UUID.str)
            it.rejectKey(Key.SAK_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("PersisterOppgaveLøser: $packet")
        val uuid = packet[Key.UUID.str].asText()
        val oppgaveId = packet[Key.OPPGAVE_ID.str].asText()
        repository.oppdaterOppgaveId(oppgaveId, uuid)
        sikkerLogger.info("PersisterOppgaveLøser: Lagret oppgaveId: $oppgaveId for uuid: $uuid")
    }
}
