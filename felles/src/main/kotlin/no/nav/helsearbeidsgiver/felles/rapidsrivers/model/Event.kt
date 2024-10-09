package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private val logger = "im-model-event".logger()
private val sikkerLogger = sikkerLogger()

fun MessageContext.publishEvent(
    eventName: EventName,
    transaksjonId: UUID?,
    forespoerselId: UUID?,
    vararg messageFields: Pair<Key, JsonElement?>,
): JsonElement {
    val optionalIdFields =
        mapOf(
            Key.UUID to transaksjonId,
            Key.FORESPOERSEL_ID to forespoerselId,
        ).mapValuesNotNull { it?.toJson() }
            .toList()
            .toTypedArray()

    val nonNullMessageFields =
        messageFields
            .toMap()
            .mapValuesNotNull { it }
            .toList()
            .toTypedArray()

    return publish(
        Key.EVENT_NAME to eventName.toJson(),
        *optionalIdFields,
        *nonNullMessageFields,
    ).also {
        logger.info("Publiserte event '$eventName' med transaksjonId '$transaksjonId'.")
        sikkerLogger.info("Publiserte event:\n${it.toPretty()}")
    }
}
