@file:UseSerializers(LocalDateSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOrganisasjonsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBehandlingsdager
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isLessThan
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.functions.validate
import org.valiktor.functions.validateForEach
import org.valiktor.validate
import java.time.LocalDate

@Serializable
data class Bruttoinntekt(
    var bekreftet: Boolean,
    var bruttoInntekt: Double,
    val endringÅrsak: ÅrsakBeregnetInntektEndringKodeliste? = null,
    val manueltKorrigert: Boolean
)

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null,
    val utbetalt: Double? = null
)

@Serializable
data class Refusjon(
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double? = null,
    val refusjonOpphører: LocalDate? = null
)

@Serializable
enum class ÅrsakBeregnetInntektEndringKodeliste {
    Tariffendring,
    FeilInntekt
}

@Serializable
data class InnsendingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    val bestemmendeFraværsdag: LocalDate,
    val fraværsperioder: List<Periode>,
    val bruttoInntekt: Bruttoinntekt,
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    val refusjon: Refusjon,
    val naturalytelser: List<Naturalytelse>? = null,
    val bekreftOpplysninger: Boolean
) {
    fun validate() {
        validate(this) {
            // Den ansatte
            validate(InnsendingRequest::orgnrUnderenhet).isNotNull()
            validate(InnsendingRequest::orgnrUnderenhet).isOrganisasjonsnummer()
            // Arbeidsgiver
            validate(InnsendingRequest::identitetsnummer).isNotNull()
            validate(InnsendingRequest::identitetsnummer).isIdentitetsnummer()
            // Fraværsperiode
            validate(InnsendingRequest::behandlingsdager).isValidBehandlingsdager() // Velg behandlingsdager
            // Egenmelding
            validate(InnsendingRequest::egenmeldingsperioder).validateForEach {
                validate(Periode::fom).isNotNull()
                validate(Periode::tom).isNotNull()
                validate(Periode::tom).isGreaterThan(it.fom)
            }
            // Brutto inntekt
            validate(InnsendingRequest::bruttoInntekt).validate {
                validate(Bruttoinntekt::bekreftet).isTrue()
                validate(Bruttoinntekt::bruttoInntekt).isGreaterThan(0.0)
                validate(Bruttoinntekt::bruttoInntekt).isLessThan(1_000_000.0)
                if (it.manueltKorrigert) {
                    validate(Bruttoinntekt::endringÅrsak).isNotNull()
                }
            }
            // Betaler arbeidsgiver full lønn til arbeidstaker
            validate(InnsendingRequest::fullLønnIArbeidsgiverPerioden).validate {
                if (!it.utbetalerFullLønn) {
                    validate(FullLønnIArbeidsgiverPerioden::begrunnelse).isNotNull()
                }
            }
            // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
            validate(InnsendingRequest::refusjon).validate {
                if (it.utbetalerHeleEllerDeler) {
                    validate(Refusjon::refusjonPrMnd).isGreaterThan(0.0)
                    validate(Refusjon::refusjonPrMnd).isLessThan(1_000_000.0)
                }
            }
            // Naturalytelser
            validate(InnsendingRequest::naturalytelser).validateForEach {
                validate(Naturalytelse::naturalytelseKode).isNotNull()
                validate(Naturalytelse::dato).isNotNull()
                validate(Naturalytelse::beløp).isNotNull()
                validate(Naturalytelse::beløp).isGreaterThan(0.0)
                validate(Naturalytelse::beløp).isLessThan(1_000_000.0)
            }
            validate(InnsendingRequest::bekreftOpplysninger).isTrue()
        }
    }
}
