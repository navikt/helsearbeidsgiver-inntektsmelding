package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

fun MessageContext.publish(
    key: UUID,
    vararg messageFields: Pair<Key, JsonElement>,
): JsonElement = publish(key.toString(), messageFields.toMap())

fun MessageContext.publish(
    key: Fnr,
    vararg messageFields: Pair<Key, JsonElement>,
): JsonElement = publish(key.verdi, messageFields.toMap())

internal fun MessageContext.publish(
    key: String,
    messageFields: Map<Key, JsonElement>,
): JsonElement =
    messageFields
        .mapKeys { (key, _) -> key.toString() }
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
