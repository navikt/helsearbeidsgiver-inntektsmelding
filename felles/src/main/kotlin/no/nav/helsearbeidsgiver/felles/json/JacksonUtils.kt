package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.json.JsonElement

fun customObjectMapper(): ObjectMapper =
    jacksonObjectMapper().configure()

private val objectMapper = customObjectMapper()

fun ObjectMapper.configure(): ObjectMapper =
    registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

fun JsonElement.toJsonNode(): JsonNode =
    toString().let(objectMapper::readTree)
