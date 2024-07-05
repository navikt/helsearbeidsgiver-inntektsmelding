package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.MessageContext
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

private val logger = "im-model-data".logger()
private val sikkerLogger = sikkerLogger()

fun MessageContext.publishData(
    eventName: EventName,
    transaksjonId: UUID,
    forespoerselId: UUID?,
    vararg messageFields: Pair<Key, JsonElement?>
): JsonElement {
    val optionalIdFields = mapOf(
        Key.FORESPOERSEL_ID to forespoerselId
    )
        .mapValuesNotNull { it?.toJson() }
        .toList()
        .toTypedArray()

    val nonNullMessageFields = messageFields.toMap().mapValuesNotNull { it }

    return publish(
        Key.EVENT_NAME to eventName.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to nonNullMessageFields.toJson(),
        *optionalIdFields,
        *nonNullMessageFields.toList().toTypedArray()
    )
        .also {
            logger.info("Publiserte data for '$eventName' med transaksjonId '$transaksjonId'.")
            sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
        }
}
