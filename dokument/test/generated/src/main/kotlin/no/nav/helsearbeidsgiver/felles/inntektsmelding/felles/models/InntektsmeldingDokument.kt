package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.validation.Valid
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern
import kotlin.String
import kotlin.collections.List

data class InntektsmeldingDokument(
    @param:JsonProperty("orgnrUnderenhet")
    @get:JsonProperty("orgnrUnderenhet")
    @get:NotNull
    val orgnrUnderenhet: String,
    @param:JsonProperty("identitetsnummer")
    @get:JsonProperty("identitetsnummer")
    @get:NotNull
    val identitetsnummer: String,
    @param:JsonProperty("fulltNavn")
    @get:JsonProperty("fulltNavn")
    @get:NotNull
    val fulltNavn: String,
    @param:JsonProperty("virksomhetNavn")
    @get:JsonProperty("virksomhetNavn")
    @get:NotNull
    val virksomhetNavn: String,
    @param:JsonProperty("behandlingsdager")
    @get:JsonProperty("behandlingsdager")
    val behandlingsdager: List<LocalDate>? = null,
    @param:JsonProperty("egenmeldingsperioder")
    @get:JsonProperty("egenmeldingsperioder")
    @get:NotNull
    @get:Valid
    val egenmeldingsperioder: List<Periode>,
    @param:JsonProperty("bestemmendeFraværsdag")
    @get:JsonProperty("bestemmendeFraværsdag")
    @get:NotNull
    val bestemmendeFraværsdag: LocalDate,
    @param:JsonProperty("fraværsperioder")
    @get:JsonProperty("fraværsperioder")
    @get:NotNull
    @get:Valid
    val fraværsperioder: List<Periode>,
    @param:JsonProperty("arbeidsgiverperioder")
    @get:JsonProperty("arbeidsgiverperioder")
    @get:NotNull
    @get:Valid
    val arbeidsgiverperioder: List<Periode>,
    @param:JsonProperty("beregnetInntekt")
    @get:JsonProperty("beregnetInntekt")
    @get:NotNull
    val beregnetInntekt: BigDecimal,
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
    @param:JsonProperty("tidspunkt")
    @get:JsonProperty("tidspunkt")
    @get:NotNull
    @get:Pattern(regexp = "yyyy-MM-dd'T'HH:mm:ss.SSSxx")
    val tidspunkt: OffsetDateTime,
    @param:JsonProperty("årsakInnsending")
    @get:JsonProperty("årsakInnsending")
    val årsakInnsending: ÅrsakInnsending? = null,
    @param:JsonProperty("identitetsnummerInnsender")
    @get:JsonProperty("identitetsnummerInnsender")
    val identitetsnummerInnsender: String? = null
)
