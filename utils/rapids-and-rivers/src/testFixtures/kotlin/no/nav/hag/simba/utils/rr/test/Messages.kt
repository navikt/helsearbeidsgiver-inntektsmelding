package no.nav.hag.simba.utils.rr.test

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

@JvmInline
value class Messages(
    private val value: MutableList<JsonElement> = mutableListOf(),
) {
    internal fun add(json: String) {
        json
            .parseJson()
            .let(value::add)
    }

    internal fun reset() {
        value.clear()
    }

    fun firstAsMap(): Map<Key, JsonElement> =
        value
            .firstOrNull()
            .shouldNotBeNull()
            .toMap()

    fun all(): List<JsonElement> = value

    fun filter(eventName: EventName): Messages =
        filter { msg ->
            msg
                .toMap()[Key.EVENT_NAME]
                ?.runCatching { fromJson(EventName.serializer()) }
                ?.map { it == eventName }
                ?.getOrElse { false }
                .orDefault(false)
        }

    fun filter(behovType: BehovType): Messages =
        filter { msg ->
            msg
                .toMap()[Key.BEHOV]
                ?.runCatching { fromJson(BehovType.serializer()) }
                ?.map { it == behovType }
                ?.getOrElse { false }
                .orDefault(false)
        }

    fun filter(dataKey: Key): Messages =
        filter { msg ->
            val data = msg.toMap()[Key.DATA]?.runCatching { toMap() }?.getOrNull()
            data != null && dataKey in data
        }

    fun filterFeil(): Messages =
        filter { msg ->
            msg.toMap().containsKey(Key.FAIL)
        }

    private fun filter(predicate: (JsonElement) -> Boolean): Messages =
        value
            .filter(predicate)
            .toMutableList()
            .let(::Messages)
}
