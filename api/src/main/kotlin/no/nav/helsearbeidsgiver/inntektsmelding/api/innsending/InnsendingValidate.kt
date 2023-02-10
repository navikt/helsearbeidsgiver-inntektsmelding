package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOrganisasjonsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBehandlingsdager
import org.valiktor.functions.isGreaterThan
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
            validate(Periode::fom).isNotNull()
            validate(Periode::tom).isNotNull()
            validate(Periode::tom).isGreaterThan(it.fom)
        }
        // Brutto inntekt
        validate(InnsendingRequest::inntekt).validate {
            validate(Inntekt::bekreftet).isTrue()
            validate(Inntekt::beregnetInntekt).isGreaterThan(0.0)
            validate(Inntekt::beregnetInntekt).isLessThan(1_000_000.0)
            if (it.manueltKorrigert) {
                validate(Inntekt::endringÅrsak).isNotNull()
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
            validate(Naturalytelse::naturalytelse).isNotNull()
            validate(Naturalytelse::dato).isNotNull()
            validate(Naturalytelse::beløp).isNotNull()
            validate(Naturalytelse::beløp).isGreaterThan(0.0)
            validate(Naturalytelse::beløp).isLessThan(1_000_000.0)
        }
        validate(InnsendingRequest::bekreftOpplysninger).isTrue()
    }
}
