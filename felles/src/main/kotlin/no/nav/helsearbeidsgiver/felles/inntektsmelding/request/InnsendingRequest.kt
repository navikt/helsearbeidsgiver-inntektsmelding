@file:UseSerializers(LocalDateSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles.inntektsmelding.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.InntektEndringÅrsak
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class InnsendingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    val bestemmendeFraværsdag: LocalDate,
    val fraværsperioder: List<Periode>,
    val inntekt: Inntekt,
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    val refusjon: Refusjon,
    val naturalytelser: List<Naturalytelse>? = null,
    val årsakInnsending: ÅrsakInnsending,
    val bekreftOpplysninger: Boolean
)

@Serializable
data class Inntekt(
    var bekreftet: Boolean,
    var beregnetInntekt: Double,
    val endringÅrsak: InntektEndringÅrsak? = null,
    val manueltKorrigert: Boolean
)
