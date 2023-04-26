package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import javax.validation.constraints.NotNull

data class Naturalytelse(
    @param:JsonProperty("naturalytelse")
    @get:JsonProperty("naturalytelse")
    @get:NotNull
    val naturalytelse: NaturalytelseKode,
    @param:JsonProperty("dato")
    @get:JsonProperty("dato")
    @get:NotNull
    val dato: LocalDate,
    @param:JsonProperty("beløp")
    @get:JsonProperty("beløp")
    @get:NotNull
    val beløp: BigDecimal
)
