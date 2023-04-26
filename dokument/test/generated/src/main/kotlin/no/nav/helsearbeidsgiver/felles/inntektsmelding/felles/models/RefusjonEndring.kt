package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

data class RefusjonEndring(
    @param:JsonProperty("beløp")
    @get:JsonProperty("beløp")
    val beløp: BigDecimal? = null,
    @param:JsonProperty("dato")
    @get:JsonProperty("dato")
    val dato: LocalDate? = null
)
