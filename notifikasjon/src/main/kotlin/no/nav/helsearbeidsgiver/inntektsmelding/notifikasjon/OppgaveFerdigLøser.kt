package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.OppgaveFerdigLøsning
import no.nav.helsearbeidsgiver.utils.log.logger

class OppgaveFerdigLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : River.PacketListener {

    private val logger = logger()
    private val BEHOV = BehovType.ENDRE_OPPGAVE_STATUS

    init {
        logger.info("Starter OppgaveFerdigLøser...")
        River(rapidsConnection).apply {
            validate {
                it.requireAll(Key.BEHOV.str, BEHOV)
                it.requireKey(DataFelt.OPPGAVE_ID.str)
                it.requireKey(Key.UUID.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val oppgaveId = packet[DataFelt.OPPGAVE_ID.str].asText()
        logger.info("OppgaveFerdigLøser skal markere oppgaveId: $oppgaveId som utført...")
        try {
            runBlocking {
                arbeidsgiverNotifikasjonKlient.oppgaveUtfoert(oppgaveId)
            }
            publiserLøsning(OppgaveFerdigLøsning(oppgaveId), packet, context)
            logger.info("OppgaveFerdigLøser markerte oppgaveId: $oppgaveId som utført!")
        } catch (ex: Exception) {
            logger.error("OppgaveFerdigLøser klarte ikke ferdigstile oppgaveId: $oppgaveId!")
            sikkerLogger.error("OppgaveFerdigLøser klarte ikke ferdigstile oppgaveId: $oppgaveId!", ex)
            publiserLøsning(OppgaveFerdigLøsning(error = Feilmelding("OppgaveFerdigLøser klarte ikke ferdigstile oppgaveId: $oppgaveId!")), packet, context)
        }
    }

    private fun publiserLøsning(løsning: OppgaveFerdigLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
