@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles.inntektsmelding.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.RefusjonEndring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakBeregnetInntektEndringKodeliste
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateTimeSerializer
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class InntektsmeldingDokument(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    var fulltNavn: String,
    var virksomhetNavn: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFraværsdag: LocalDate,
    val fraværsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    var beregnetInntekt: Double,
    val beregnetInntektEndringÅrsak: ÅrsakBeregnetInntektEndringKodeliste? = null,
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    val refusjon: Refusjon,
    val naturalytelser: List<Naturalytelse>? = null,
    val tidspunkt: LocalDateTime,
    val årsakInnsending: ÅrsakInnsending,
    val identitetsnummerInnsender: String
)

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null,
    val utbetalt: Double? = null
)

@Serializable
data class Refusjon(
    val refusjonPrMnd: Double? = null,
    val refusjonOpphører: LocalDate? = null,
    val refusjonEndringer: List<RefusjonEndring>? = null
)
