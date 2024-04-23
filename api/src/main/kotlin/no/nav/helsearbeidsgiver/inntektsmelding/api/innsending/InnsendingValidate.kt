package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOrganisasjonsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isTelefonnummer
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isGreaterThanOrEqualTo
import org.valiktor.functions.isLessThan
import org.valiktor.functions.isLessThanOrEqualTo
import org.valiktor.functions.isNotEmpty
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.functions.validate
import org.valiktor.functions.validateForEach

fun Innsending.validate() {
    org.valiktor.validate(this) { innsendt ->
        // sjekk om delvis eller komplett innsending:
        if (isKomplettForespoersel(innsendt.forespurtData)) { // komplett innsending (settes per nå fra frontend :/ )
            // validering kan komme til å divergere mer i fremtiden
            // Betaler arbeidsgiver full lønn til arbeidstaker
            validate(Innsending::fullLønnIArbeidsgiverPerioden).isNotNull() // må gjøre dette eksplisitt siden kontrakten tillater nullable
            validate(Innsending::fullLønnIArbeidsgiverPerioden).validate {
                if (!it.utbetalerFullLønn) {
                    validate(FullLoennIArbeidsgiverPerioden::begrunnelse).isNotNull()
                    validate(FullLoennIArbeidsgiverPerioden::utbetalt).isNotNull()
                    validate(FullLoennIArbeidsgiverPerioden::utbetalt).isGreaterThanOrEqualTo(0.0)
                    validate(FullLoennIArbeidsgiverPerioden::utbetalt).isLessThan(1_000_000.0)
                }
            }
        }
        // Den ansatte
        validate(Innsending::orgnrUnderenhet).isNotNull()
        validate(Innsending::orgnrUnderenhet).isOrganisasjonsnummer()
        // Arbeidsgiver
        validate(Innsending::identitetsnummer).isNotNull()
        validate(Innsending::identitetsnummer).isIdentitetsnummer()
        // valider telefon
        validate(Innsending::telefonnummer).isTelefonnummer()
        // Arbeidsgiverperioder
        validate(Innsending::arbeidsgiverperioder).validateForEach {
            validate(Periode::fom).isNotNull()
            validate(Periode::tom).isNotNull()
            validate(Periode::tom).isGreaterThanOrEqualTo(it.fom)
        }
        // Er tillatt å unngå arbeidsgiverperioder når:
        // - arbeidsgiver ikke betaler lønn i arbeidsgiverperioden
        if (innsendt.fullLønnIArbeidsgiverPerioden?.utbetalerFullLønn == true) {
            validate(Innsending::arbeidsgiverperioder).isNotEmpty()
        }
        // Egenmelding
        validate(Innsending::egenmeldingsperioder).validateForEach {
            validate(Periode::fom).isNotNull()
            validate(Periode::tom).isNotNull()
            validate(Periode::tom).isGreaterThanOrEqualTo(it.fom)
        }
        // Brutto inntekt
        validate(Innsending::inntekt).validate {
            validate(Inntekt::bekreftet).isTrue()
            validate(Inntekt::beregnetInntekt).isGreaterThanOrEqualTo(0.0)
            validate(Inntekt::beregnetInntekt).isLessThan(1_000_000.0)
            if (it.manueltKorrigert) {
                validate(Inntekt::endringÅrsak).isNotNull()
            }
        }
        // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
        validate(Innsending::refusjon).validate {
            if (it.utbetalerHeleEllerDeler) {
                validate(Refusjon::refusjonPrMnd).isNotNull()
                validate(Refusjon::refusjonPrMnd).isGreaterThanOrEqualTo(0.0)
                validate(Refusjon::refusjonPrMnd).isLessThan(1_000_000.0)
                validate(Refusjon::refusjonPrMnd).isLessThanOrEqualTo(innsendt.inntekt.beregnetInntekt)

                validate(Refusjon::refusjonEndringer).validateForEach {
                    validate(RefusjonEndring::beløp).isNotNull()
                    validate(RefusjonEndring::beløp).isGreaterThanOrEqualTo(0.0)
                    validate(RefusjonEndring::beløp).isLessThan(1_000_000.0)
                    validate(RefusjonEndring::beløp).isLessThanOrEqualTo(innsendt.inntekt.beregnetInntekt)
                    validate(RefusjonEndring::dato).isNotNull()
                }
            }
        }
        // Naturalytelser
        validate(Innsending::naturalytelser).validateForEach {
            validate(Naturalytelse::naturalytelse).isNotNull()
            validate(Naturalytelse::dato).isNotNull()
            validate(Naturalytelse::beløp).isNotNull()
            validate(Naturalytelse::beløp).isGreaterThan(0.0)
            validate(Naturalytelse::beløp).isLessThan(1_000_000.0)
        }
        validate(Innsending::bekreftOpplysninger).isTrue()
    }
}

fun isKomplettForespoersel(forespurtData: List<String>?): Boolean {
    // TODO: Midlertidig funksjon - heller enn å la frontend fortelle oss, bør vi sjekke om dette er delvis eller komplett forespørsel på backend
    return forespurtData.isNullOrEmpty() || forespurtData.size > 2
}
