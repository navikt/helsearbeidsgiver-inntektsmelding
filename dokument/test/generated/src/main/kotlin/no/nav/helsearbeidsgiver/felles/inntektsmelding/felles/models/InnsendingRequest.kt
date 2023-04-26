package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import javax.validation.Valid
import javax.validation.constraints.NotNull
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

data class InnsendingRequest(
    @param:JsonProperty("orgnrUnderenhet")
    @get:JsonProperty("orgnrUnderenhet")
    @get:NotNull
    val orgnrUnderenhet: String,
    @param:JsonProperty("identitetsnummer")
    @get:JsonProperty("identitetsnummer")
    @get:NotNull
    val identitetsnummer: String,
    @param:JsonProperty("behandlingsdager")
    @get:JsonProperty("behandlingsdager")
    @get:NotNull
    val behandlingsdager: List<LocalDate>,
    @param:JsonProperty("egenmeldingsperioder")
    @get:JsonProperty("egenmeldingsperioder")
    @get:NotNull
    @get:Valid
    val egenmeldingsperioder: List<Periode>,
    @param:JsonProperty("arbeidsgiverperioder")
    @get:JsonProperty("arbeidsgiverperioder")
    @get:NotNull
    @get:Valid
    val arbeidsgiverperioder: List<Periode>,
    @param:JsonProperty("bestemmendeFraværsdag")
    @get:JsonProperty("bestemmendeFraværsdag")
    @get:NotNull
    val bestemmendeFraværsdag: LocalDate,
    @param:JsonProperty("fraværsperioder")
    @get:JsonProperty("fraværsperioder")
    @get:NotNull
    @get:Valid
    val fraværsperioder: List<Periode>,
    @param:JsonProperty("inntekt")
    @get:JsonProperty("inntekt")
    @get:NotNull
    @get:Valid
    val inntekt: Inntekt,
    @param:JsonProperty("fullLønnIArbeidsgiverPerioden")
    @get:JsonProperty("fullLønnIArbeidsgiverPerioden")
    @get:NotNull
    @get:Valid
    val fullLønnIArbeidsgiverPerioden: FullLonnIArbeidsgiverPerioden,
    @param:JsonProperty("refusjon")
    @get:JsonProperty("refusjon")
    @get:NotNull
    @get:Valid
    val refusjon: Refusjon,
    @param:JsonProperty("naturalytelser")
    @get:JsonProperty("naturalytelser")
    @get:Valid
    val naturalytelser: List<Naturalytelse>? = null,
    @param:JsonProperty("årsakInnsending")
    @get:JsonProperty("årsakInnsending")
    @get:NotNull
    val årsakInnsending: ÅrsakInnsending,
    @param:JsonProperty("bekreftOpplysninger")
    @get:JsonProperty("bekreftOpplysninger")
    @get:NotNull
    val bekreftOpplysninger: Boolean
)
