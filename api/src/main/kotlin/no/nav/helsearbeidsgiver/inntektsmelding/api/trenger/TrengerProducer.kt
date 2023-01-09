package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.util.UUID

class TrengerProducer(
    private val rapidsConnection: RapidsConnection
) {
    init {
        logger.info("Starter TrengerProducer...")
    }

    fun publish(request: TrengerRequest): String {
        val uuid = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to "HENT_TRENGER_INNTEKT",
                Key.BEHOV.str to listOf(
                    BehovType.HENT_TRENGER_IM.name
                ),
                Key.UUID.str to uuid,
                Key.VEDTAKSPERIODE_ID.str to request.uuid
            )
        )
        rapidsConnection.publish(uuid.toString(), packet.toJson())
        logger.info("Publiserte trenger behov id=$uuid")
        sikkerlogg.info("Publiserte trenger behov id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
