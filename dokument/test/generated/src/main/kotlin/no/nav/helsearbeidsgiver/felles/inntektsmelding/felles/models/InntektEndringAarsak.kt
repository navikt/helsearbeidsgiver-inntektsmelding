package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate
import javax.validation.Valid
import javax.validation.constraints.NotNull
import kotlin.String
import kotlin.collections.List

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "typpe",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = Tariffendring::class,
        name =
        "Tariffendring"
    ),
    JsonSubTypes.Type(value = Ferie::class, name = "Ferie"),
    JsonSubTypes.Type(
        value =
        VarigLonnsendring::class,
        name = "VarigLonnsendring"
    ),
    JsonSubTypes.Type(
        value =
        NyStilling::class,
        name = "NyStilling"
    ),
    JsonSubTypes.Type(
        value = NyStillingsprosent::class,
        name = "NyStillingsprosent"
    ),
    JsonSubTypes.Type(
        value = Bonus::class,
        name =
        "Bonus"
    ),
    JsonSubTypes.Type(value = Permisjon::class, name = "Permisjon"),
    JsonSubTypes.Type(
        value =
        Permittering::class,
        name = "Permittering"
    )
)
sealed class InntektEndringAarsak() {
    abstract val typpe: String
}

data class Tariffendring(
    @param:JsonProperty("gjelderFra")
    @get:JsonProperty("gjelderFra")
    @get:NotNull
    val gjelderFra: LocalDate,
    @param:JsonProperty("bleKjent")
    @get:JsonProperty("bleKjent")
    @get:NotNull
    val bleKjent: LocalDate
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "Tariffendring"
}

data class Ferie(
    @param:JsonProperty("liste")
    @get:JsonProperty("liste")
    @get:NotNull
    @get:Valid
    val liste: List<Periode>
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "Ferie"
}

data class VarigLonnsendring(
    @param:JsonProperty("gjelderFra")
    @get:JsonProperty("gjelderFra")
    @get:NotNull
    val gjelderFra: LocalDate
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "VarigLonnsendring"
}

data class NyStilling(
    @param:JsonProperty("gjelderFra")
    @get:JsonProperty("gjelderFra")
    @get:NotNull
    val gjelderFra: LocalDate
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "NyStilling"
}

data class NyStillingsprosent(
    @param:JsonProperty("gjelderFra")
    @get:JsonProperty("gjelderFra")
    @get:NotNull
    val gjelderFra: LocalDate
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "NyStillingsprosent"
}

class Bonus() : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "Bonus"
}

data class Permisjon(
    @param:JsonProperty("liste")
    @get:JsonProperty("liste")
    @get:NotNull
    @get:Valid
    val liste: List<Periode>
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "Permisjon"
}

data class Permittering(
    @param:JsonProperty("liste")
    @get:JsonProperty("liste")
    @get:NotNull
    @get:Valid
    val liste: List<Periode>
) : InntektEndringAarsak() {
    @get:JsonProperty("typpe")
    @get:NotNull
    override val typpe: String = "Permittering"
}
