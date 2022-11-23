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
    val endringaarsak: String? = null, // TODO Lage enum?
    val manueltKorrigert: Boolean
)

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null
)

@Serializable
data class HeleEllerdeler(
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double? = null,
    val opphørSisteDag: LocalDate? = null
)

@Serializable
data class InnsendingRequest(
    val orgnrUnderenhet: String, // OK
    val identitetsnummer: String, // OK
    val behandlingsdager: List<LocalDate>, // OK
    val egenmeldingsperioder: List<EgenmeldingPeriode>,
    val bruttoInntekt: Bruttoinntekt,
    // Betaljer arbeidsgiver full lønn til arbeidstaker
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
    val heleEllerdeler: HeleEllerdeler,
    // Naturalytelser
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
                validate(EgenmeldingPeriode::fom).isNotNull()
                validate(EgenmeldingPeriode::tom).isNotNull()
                validate(EgenmeldingPeriode::tom).isGreaterThan(it.fom)
            }
            // Brutto inntekt
            validate(InnsendingRequest::bruttoInntekt).validate {
                validate(Bruttoinntekt::bekreftet).isTrue()
                validate(Bruttoinntekt::bruttoInntekt).isGreaterThan(0.0)
                validate(Bruttoinntekt::bruttoInntekt).isLessThan(1_000_000.0)
                if (it.manueltKorrigert) {
                    validate(Bruttoinntekt::endringaarsak).isNotNull()
                }
            }
            // Betaler arbeidsgiver full lønn til arbeidstaker
            validate(InnsendingRequest::fullLønnIArbeidsgiverPerioden).validate {
                if (!it.utbetalerFullLønn) {
                    validate(FullLønnIArbeidsgiverPerioden::begrunnelse).isNotNull()
                }
            }
            // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
            validate(InnsendingRequest::heleEllerdeler).validate {
                if (it.utbetalerHeleEllerDeler) {
                    validate(HeleEllerdeler::refusjonPrMnd).isGreaterThan(0.0)
                    validate(HeleEllerdeler::refusjonPrMnd).isLessThan(1_000_000.0)
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
