package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.nyStatusSakByGrupperingsid
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.SakFerdigLøsning
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SakFerdigLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.ENDRE_SAK_STATUS

    init {
        logger.info("Starter SakFerdigLøser...")
        River(rapidsConnection).apply {
            validate {
                it.requireAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.UUID.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("SakFerdigLøser fikk pakke: ${packet.toJson()}")
        val forespoerselId = packet[Key.UUID.str].asText()
        logger.info("SakFerdigLøser skal ferdigstille forespoerselId: $forespoerselId som utført...")
        val innsendingstidspunkt = LocalDate.now()
        val dato = innsendingstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        try {
            runBlocking {
                arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    forespoerselId,
                    "Mottatt $dato",
                    SaksStatus.FERDIG
                )
            }
            val løsning = SakFerdigLøsning(forespoerselId)
            publiserLøsning(løsning, packet, context)
            logger.info("SakFerdigLøser ferdigstilte for forespoerselId: $forespoerselId som utført!")
        } catch (ex: Exception) {
            logger.error("SakFerdigLøser klarte ikke ferdigstille forespoerselId: $forespoerselId!")
            sikkerLogger.error("SakFerdigLøser klarte ikke ferdigstille forespoerselId: $forespoerselId!", ex)
            publiserLøsning(SakFerdigLøsning(error = Feilmelding("Klarte ikke ferdigstille forespoerselId: $forespoerselId!")), packet, context)
        }
    }

    fun publiserLøsning(løsning: SakFerdigLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
