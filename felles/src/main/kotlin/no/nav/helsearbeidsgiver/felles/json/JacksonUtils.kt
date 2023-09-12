package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.json.JsonElement

private val objectMapper = customObjectMapper()

fun JsonElement.toJsonNode(): JsonNode =
    toString().let(objectMapper::readTree)

object Jackson {
    val objectMapper = customObjectMapper()

    inline fun <reified T : Any> fromJson(json: String): T =
        objectMapper.readValue(json, T::class.java)

    fun <T : Any> toJson(value: T): String =
        objectMapper.writeValueAsString(value)
}

private fun customObjectMapper(): ObjectMapper =
    jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true)
