package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class InntektProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter InntektProducer...")
    }

    fun publish(request: InntektRequest): UUID {
        val clientID = UUID.randomUUID()
        val spleisForesporselId = request.forespoerselId

        rapid.publish(
            Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.CLIENT_ID to clientID.toJson(UuidSerializer),
            Key.FORESPOERSEL_ID to spleisForesporselId.toJson(),
            DataFelt.INNTEKT_DATO to request.skjaeringstidspunkt.toJson(LocalDateSerializer)
        )

        logger.info("Publiserte Behov: ${BehovType.HENT_TRENGER_IM} for spleisId $spleisForesporselId (oppdater inntekt)")

        return clientID
    }
}
