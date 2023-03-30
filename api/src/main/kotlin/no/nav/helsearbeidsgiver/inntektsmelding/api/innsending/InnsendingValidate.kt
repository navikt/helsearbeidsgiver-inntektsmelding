package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOrganisasjonsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBehandlingsdager
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isGreaterThanOrEqualTo
import org.valiktor.functions.isLessThan
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.functions.validate
import org.valiktor.functions.validateForEach

fun InnsendingRequest.validate() {
    org.valiktor.validate(this) {
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
            it.fom
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode::fom).isNotNull()
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode::tom).isNotNull()
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode::tom).isGreaterThanOrEqualTo(it.fom)
        }
        // Brutto inntekt
        validate(InnsendingRequest::inntekt).validate {
            validate(Inntekt::bekreftet).isTrue()
            validate(Inntekt::beregnetInntekt).isGreaterThan(0.0.toBigDecimal())
            validate(Inntekt::beregnetInntekt).isLessThan(1_000_000.0.toBigDecimal())
            if (it.manueltKorrigert) {
                validate(Inntekt::endringÅrsak).isNotNull()
            }
        }
        // Betaler arbeidsgiver full lønn til arbeidstaker
        validate(InnsendingRequest::fullLønnIArbeidsgiverPerioden).validate {
            if (!it.utbetalerFullLønn) {
                validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden::begrunnelse).isNotNull()
            }
        }
        // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
        validate(InnsendingRequest::refusjon).validate {
            if (it.utbetalerHeleEllerDeler) {
                validate(Refusjon::refusjonPrMnd).isGreaterThan(0.0.toBigDecimal())
                validate(Refusjon::refusjonPrMnd).isLessThan(1_000_000.0.toBigDecimal())
            }
        }
        // Naturalytelser
        validate(InnsendingRequest::naturalytelser).validateForEach {
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse::naturalytelse).isNotNull()
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse::dato).isNotNull()
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse::beløp).isNotNull()
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse::beløp).isGreaterThan(0.0.toBigDecimal())
            validate(no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse::beløp).isLessThan(1_000_000.0.toBigDecimal())
        }
        validate(InnsendingRequest::bekreftOpplysninger).isTrue()
    }
}
