package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun MessageContext.publish(vararg messageFields: Pair<Key, JsonElement>): JsonElement = publish(messageFields.toMap())

fun MessageContext.publish(messageFields: Map<Key, JsonElement>): JsonElement =
    messageFields
        .let { root ->
            root
                .plus(Key.KONTEKST_ID to root[Key.UUID])
                .mapValuesNotNull { it }
        }.mapKeys { (key, _) -> key.toString() }
        .filterValues { it !is JsonNull }
        .toJson()
        .toString()
        .let {
            JsonMessage(
                originalMessage = it,
                problems = MessageProblems(it),
                metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                randomIdGenerator = null,
            )
        }.toJson()
        .also(::publish)
        .parseJson()
