package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import javax.validation.Valid
import javax.validation.constraints.NotNull
import kotlin.Boolean

data class Inntekt(
    @param:JsonProperty("bekreftet")
    @get:JsonProperty("bekreftet")
    @get:NotNull
    val bekreftet: Boolean,
    @param:JsonProperty("beregnetInntekt")
    @get:JsonProperty("beregnetInntekt")
    @get:NotNull
    val beregnetInntekt: BigDecimal,
    @param:JsonProperty("endringÅrsak")
    @get:JsonProperty("endringÅrsak")
    @get:Valid
    val endringÅrsak: InntektEndringAarsak? = null,
    @param:JsonProperty("manueltKorrigert")
    @get:JsonProperty("manueltKorrigert")
    @get:NotNull
    val manueltKorrigert: Boolean
)
