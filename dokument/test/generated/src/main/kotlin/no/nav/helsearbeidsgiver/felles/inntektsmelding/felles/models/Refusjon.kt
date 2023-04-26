package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import javax.validation.Valid
import javax.validation.constraints.NotNull
import kotlin.Boolean
import kotlin.collections.List

data class Refusjon(
    @param:JsonProperty("utbetalerHeleEllerDeler")
    @get:JsonProperty("utbetalerHeleEllerDeler")
    @get:NotNull
    val utbetalerHeleEllerDeler: Boolean,
    @param:JsonProperty("refusjonPrMnd")
    @get:JsonProperty("refusjonPrMnd")
    val refusjonPrMnd: BigDecimal? = null,
    @param:JsonProperty("refusjonOpphører")
    @get:JsonProperty("refusjonOpphører")
    val refusjonOpphører: LocalDate? = null,
    @param:JsonProperty("refusjonEndringer")
    @get:JsonProperty("refusjonEndringer")
    @get:Valid
    val refusjonEndringer: List<RefusjonEndring>? = null
)
