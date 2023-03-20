package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterSakIdLøsning
import org.slf4j.LoggerFactory

class PersisterSakLøser(
    rapidsConnection: RapidsConnection,
    val repository: Repository
) : River.PacketListener {

    private val BEHOV = BehovType.PERSISTER_SAK_ID
    private val sikkerLogger = LoggerFactory.getLogger("tjenestekall")

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, listOf(BehovType.PERSISTER_SAK_ID.name))
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.SAK_ID.str)
                // it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("PersisterSakLøser: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        //repository.oppdaterSakId(sakId, uuid)
        //publiserLøsning(PersisterSakIdLøsning(sakId), packet, context)
        logger.info("PersisterSakLøser: Lagret sakId: $sakId for uuid: $uuid")
    }

    fun publiserLøsning(løsning: PersisterSakIdLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
