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
    fun all(): List<JsonElement> =
        value

    fun add(json: String) {
        json.parseJson()
            .let(value::add)
    }

    fun reset() {
        value.clear()
    }

    fun find(
        event: EventName,
        behovType: BehovType? = null,
        dataFelt: DataFelt? = null,
        loesning: Boolean = false
    ): JsonElement =
        value.firstOrNull { jsonMsg ->
            val msg = jsonMsg.fromJsonMapOnlyKeys()

            val matchesEvent = event == msg[Key.EVENT_NAME]?.fromJson(EventName.serializer())

            val matchesBehovType = behovType == null ||
                msg[Key.BEHOV]?.fromJsonToBehovTypeListe()
                    ?.contains(behovType)
                    .orDefault(false)

            val matchesDataFelt = dataFelt == null ||
                (msg.contains(Key.DATA) && jsonMsg.fromJsonMapFiltered(DataFelt.serializer()).contains(dataFelt))

            val containsLoesning = !loesning ||
                (behovType == null && msg.contains(Key.LØSNING)) ||
                (behovType != null && msg[Key.LØSNING]?.fromJsonMapFiltered(BehovType.serializer())?.contains(behovType).orDefault(false))

            matchesEvent && matchesBehovType && matchesDataFelt && containsLoesning
        }
            .shouldNotBeNull()
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
