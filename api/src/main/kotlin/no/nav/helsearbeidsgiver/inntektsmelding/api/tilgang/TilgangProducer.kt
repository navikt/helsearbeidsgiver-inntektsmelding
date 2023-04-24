package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.util.UUID

class TilgangProducer(private val rapid: RapidsConnection) {

    init {
        logger.info("Starter TilgangProducer...")
    }

    fun publish(fnr: String, forespørselId: String, initiateId: UUID = UUID.randomUUID()): UUID {
        rapid.publish(
            Key.EVENT_NAME to EventName.HENT_PREUTFYLT.name.toJson(),
            Key.BEHOV to listOf(BehovType.HENT_IM_ORGNR).toJson(BehovType.serializer()),
            Key.IDENTITETSNUMMER to fnr.toJson(),
            Key.FORESPOERSEL_ID to forespørselId.toJson(),
            Key.BOOMERANG to mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.TILGANGSKONTROLL).toJson(BehovType.serializer())
            ).toJson()
        ) {
            logger.info("Publiserte tilgang behov id=$initiateId")
            sikkerlogg.info("Publiserte tilgang behov id=$initiateId json=${it.toJson()}")
        }

        return initiateId
    }
}
