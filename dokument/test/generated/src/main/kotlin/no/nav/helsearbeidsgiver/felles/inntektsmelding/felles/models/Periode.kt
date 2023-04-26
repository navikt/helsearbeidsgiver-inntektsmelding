package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import javax.validation.constraints.NotNull

data class Periode(
    @param:JsonProperty("fom")
    @get:JsonProperty("fom")
    @get:NotNull
    val fom: LocalDate,
    @param:JsonProperty("tom")
    @get:JsonProperty("tom")
    @get:NotNull
    val tom: LocalDate
)
