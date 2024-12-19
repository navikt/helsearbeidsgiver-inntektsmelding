package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.inntektsmeldingkontrakt.Inntektsmelding

object Jackson {
    private val objectMapper: ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true)

    fun fromJson(json: String): Inntektsmelding = objectMapper.readValue(json, Inntektsmelding::class.java)

    fun toJson(im: Inntektsmelding): String = objectMapper.writeValueAsString(im)
}
