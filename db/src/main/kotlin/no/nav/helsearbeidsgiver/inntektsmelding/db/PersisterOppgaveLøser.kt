package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

class PersisterOppgaveLøser(
    rapidsConnection: RapidsConnection,
    val repository: Repository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, listOf(BehovType.PERSISTER_OPPGAVE_ID.name))
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.OPPGAVE_ID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("PersisterOppgaveLøser mottok pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val oppgaveId = packet[Key.OPPGAVE_ID.str].asText()
        repository.oppdaterOppgaveId(oppgaveId, uuid)
        sikkerLogger.info("PersisterOppgaveLøser lagret oppgaveId $oppgaveId for uuid $uuid")
    }
}
