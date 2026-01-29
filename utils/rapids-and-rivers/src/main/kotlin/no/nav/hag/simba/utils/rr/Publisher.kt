package no.nav.hag.simba.utils.rr

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.hag.simba.utils.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class Publisher internal constructor(
    private val messageContext: MessageContext,
) {
    fun publish(
        key: UUID,
        vararg messageFields: Pair<Key, JsonElement>,
    ): JsonElement = messageContext.publish(key.toString(), messageFields.toMap())

    fun publish(
        key: Fnr,
        vararg messageFields: Pair<Key, JsonElement>,
    ): JsonElement = messageContext.publish(key.verdi, messageFields.toMap())
}

internal fun MessageContext.publish(
    key: String,
    messageFields: Map<Key, JsonElement>,
): JsonElement =
    publishMessage(
        key,
        messageFields.mapKeys { (key, _) -> key.toString() },
    )

internal fun MessageContext.publishMessage(
    key: String,
    messageFields: Map<String, JsonElement>,
): JsonElement =
    messageFields
        .filterValues { it !is JsonNull }
        .toJson()
        .toString()
        .let {
            JsonMessage(
                originalMessage = it,
                problems = MessageProblems(it),
                randomIdGenerator = null,
            )
        }.toJson()
        .also {
            publish(key, it)
        }.parseJson()
