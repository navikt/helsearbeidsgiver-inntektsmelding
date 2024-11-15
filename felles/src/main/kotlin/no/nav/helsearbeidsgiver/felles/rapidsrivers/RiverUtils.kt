package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun MessageContext.publish(vararg messageFields: Pair<Key, JsonElement>): JsonElement = publish(messageFields.toMap())

fun MessageContext.publish(messageFields: Map<Key, JsonElement>): JsonElement =
    messageFields
        .mapAddTemporaryOrgnrunderenhetKey()
        .mapKeys { (key, _) -> key.toString() }
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

private fun Map<Key, JsonElement>.mapAddTemporaryOrgnrunderenhetKey(): Map<Key, JsonElement> =
    this
        .mapDuplikertVerdi(Key.ORGNRUNDERENHET, Key.ORGNRUNDERENHET_V2)
        .mapDataMedDuplikertVerdi(Key.ORGNRUNDERENHET, Key.ORGNRUNDERENHET_V2)

private fun Map<Key, JsonElement>.mapDataMedDuplikertVerdi(
    originalKey: Key,
    duplikasjonKey: Key,
): Map<Key, JsonElement> {
    val data = this[Key.DATA]?.toMap().orEmpty()
    val verdi = data[originalKey]

    if (data.isEmpty() || verdi == null) {
        return this
    }

    return this.plus(
        Key.DATA to
            data.plus(duplikasjonKey to data[originalKey]).mapValuesNotNull { it }.toJson(),
    )
}

private fun Map<Key, JsonElement>.mapDuplikertVerdi(
    originalKey: Key,
    duplikasjonKey: Key,
): Map<Key, JsonElement> {
    if (!this.containsKey(originalKey)) {
        return this
    }

    return this.plus(duplikasjonKey to this[originalKey]).mapValuesNotNull { it }
}
