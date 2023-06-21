package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.SakFerdigLøsning
import no.nav.helsearbeidsgiver.utils.log.logger

class SakFerdigLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val logger = logger()
    private val BEHOV = BehovType.ENDRE_SAK_STATUS

    init {
        logger.info("Starter SakFerdigLøser...")
        River(rapidsConnection).apply {
            validate {
                it.requireAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.UUID.str)
                it.requireKey(DataFelt.SAK_ID.str)
                it.rejectKey(Key.LØSNING.str)
                it.interestedIn(Key.FORESPOERSEL_ID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("SakFerdigLøser fikk pakke: ${packet.toJson()}")
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val sakId = packet[DataFelt.SAK_ID.str].asText()
        logger.info("SakFerdigLøser skal ferdigstille sakId $sakId for forespoerselId: $forespoerselId som utført...")
        val lenke = "$linkUrl/im-dialog/$forespoerselId"
        try {
            runBlocking {
                arbeidsgiverNotifikasjonKlient.nyStatusSak(sakId, lenke, SaksStatus.FERDIG, "Mottatt")
            }
            publiserLøsning(SakFerdigLøsning(sakId), packet, context)
            logger.info("SakFerdigLøser ferdigstilte sakId $sakId for forespoerselId: $forespoerselId som utført!")
            sikkerLogger.info("SakFerdigLøser ferdigstilte sakId $sakId for forespoerselId: $forespoerselId som utført!")
        } catch (ex: Exception) {
            logger.error("SakFerdigLøser klarte ikke ferdigstille sakId $sakId, forespoerselId: $forespoerselId!")
            sikkerLogger.error("SakFerdigLøser klarte ikke ferdigstille sakId $sakId, forespoerselId: $forespoerselId!", ex)
            publiserLøsning(SakFerdigLøsning(error = Feilmelding("Klarte ikke ferdigstille sakId $sakId, forespoerselId: $forespoerselId!")), packet, context)
        }
    }

    private fun publiserLøsning(løsning: SakFerdigLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
