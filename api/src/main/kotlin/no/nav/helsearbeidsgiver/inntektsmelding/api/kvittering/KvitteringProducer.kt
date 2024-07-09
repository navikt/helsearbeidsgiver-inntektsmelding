package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import java.util.UUID

class KvitteringProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${KvitteringProducer::class.simpleName}...")
    }

    fun publish(
        clientId: UUID,
        foresporselId: UUID,
    ) {
        val packet: JsonMessage =
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                    Key.CLIENT_ID.str to clientId,
                    Key.FORESPOERSEL_ID.str to foresporselId,
                ),
            )
        rapid.publish(packet.toJson())
        logger.info("Publiserte kvittering requested, forespørselid=$foresporselId")
    }
}
