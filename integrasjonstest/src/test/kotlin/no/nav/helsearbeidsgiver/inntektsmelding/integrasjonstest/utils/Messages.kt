package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class Messages(
    private val value: MutableList<JsonElement> = mutableListOf()
) {
    private val mutex = Mutex()

    fun first(): JsonElement =
        withLock {
            value.firstOrNull()
        }
            .shouldNotBeNull()

    fun all(): List<JsonElement> =
        value

    fun add(json: String) {
        val parsed = json.parseJson()
        withLock {
            value.add(parsed)
        }
    }

    fun reset() {
        withLock {
            value.clear()
        }
    }

    fun filter(eventName: EventName): Messages =
        filter { msg ->
            msg.fromJsonMapOnlyKeys()[Key.EVENT_NAME]
                ?.runCatching { fromJson(EventName.serializer()) }
                ?.map { it == eventName }
                ?.getOrElse { false }
                .orDefault(false)
        }

    fun filter(behovType: BehovType, loesningPaakrevd: Boolean): Messages =
        filter { msg ->
            val msgMap = msg.fromJsonMapOnlyKeys()

            val behovTypeFunnet = msgMap[Key.BEHOV]
                ?.runCatching { fromJsonToBehovTypeListe() }
                ?.getOrElse { emptyList() }
                ?.contains(behovType)
                .orDefault(false)

            val loesningFunnet = msgMap[Key.LØSNING]
                ?.runCatching { fromJsonMapFiltered(BehovType.serializer()) }
                ?.getOrElse { emptyMap() }
                ?.contains(behovType)
                .orDefault(false)

            behovTypeFunnet && (!loesningPaakrevd || loesningFunnet)
        }

    fun filter(dataFelt: DataFelt): Messages =
        filter { msg ->
            val dataFunnet = msg.fromJsonMapOnlyKeys().contains(Key.DATA)

            val datafeltFunnet = msg.fromJsonMapFiltered(DataFelt.serializer()).contains(dataFelt)

            dataFunnet && datafeltFunnet
        }

    private fun filter(predicate: (JsonElement) -> Boolean): Messages =
        withLock {
            value.filter(predicate)
        }
            .toMutableList()
            .let(::Messages)

    private fun <T> withLock(block: () -> T): T =
        runBlocking {
            mutex.withLock {
                block()
            }
        }
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
