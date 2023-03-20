package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import org.slf4j.LoggerFactory

class PersisterOppgaveLøser(
    rapidsConnection: RapidsConnection,
    val repository: Repository
) : River.PacketListener {

    private val sikkerLogger = LoggerFactory.getLogger("tjenestekall")

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
        logger.info("PersisterOppgaveLøser: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val oppgaveId = packet[Key.OPPGAVE_ID.str].asText()
        repository.oppdaterOppgaveId(oppgaveId, uuid)
        logger.info("PersisterOppgaveLøser: Lagret oppgaveId: $oppgaveId for uuid: $uuid")
    }
}
