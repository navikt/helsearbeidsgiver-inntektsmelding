package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

@JvmInline
value class Messages(
    private val value: MutableList<JsonElement> = mutableListOf()
) {
    fun first(): JsonElement =
        value.firstOrNull().shouldNotBeNull()

    fun all(): List<JsonElement> =
        value

    fun add(json: String) {
        json.parseJson()
            .let(value::add)
    }

    fun reset() {
        value.clear()
    }

    fun filter(eventName: EventName): Messages =
        filter { msg ->
            msg.fromJsonMapOnlyKeys()[Key.EVENT_NAME]
                ?.runCatching { fromJson(EventName.serializer()) }
                ?.map { it == eventName }
                ?.getOrElse { false }
                .orDefault(false)
        }

    fun filter(behovType: BehovType): Messages =
        filter {
            it.fromJsonMapOnlyKeys()[Key.BEHOV]
                ?.runCatching { fromJsonToBehovTypeListe() }
                ?.getOrElse { emptyList() }
                ?.contains(behovType)
                .orDefault(false)
        }

    fun filter(dataFelt: DataFelt, utenDataKey: Boolean = false): Messages =
        filter { msg ->
            val dataFunnet = utenDataKey || msg.fromJsonMapOnlyKeys().contains(Key.DATA)

            val datafeltFunnet = msg.fromJsonMapFiltered(DataFelt.serializer()).contains(dataFelt)

            dataFunnet && datafeltFunnet
        }

    fun filterFeil(): Messages =
        filter { msg ->
            msg.fromJsonMapOnlyKeys().contains(Key.FAIL)
        }

    private fun filter(predicate: (JsonElement) -> Boolean): Messages =
        value.filter(predicate)
            .toMutableList()
            .let(::Messages)
}

private fun JsonElement.fromJsonToBehovTypeListe(): List<BehovType> =
    when (this) {
        is JsonPrimitive ->
            fromJson(BehovType.serializer()).let(::listOf)
        is JsonArray ->
            fromJson(BehovType.serializer().list())
        else ->
            emptyList()
    }
