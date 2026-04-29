package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

object Jackson {
    private val objectMapper: ObjectMapper =
        jsonMapper {
            addModule(kotlinModule())
            disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
        }

    fun fromJson(json: String): Inntektsmelding = objectMapper.readValue(json, Inntektsmelding::class.java)

    fun toJson(im: Inntektsmelding): String = objectMapper.writeValueAsString(im)
}
