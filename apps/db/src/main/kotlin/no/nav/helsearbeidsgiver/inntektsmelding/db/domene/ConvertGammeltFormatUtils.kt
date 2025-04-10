@file:Suppress("DEPRECATION")

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag

fun InntektsmeldingGammeltFormat.convertAgp(): Arbeidsgiverperiode? =
    if (arbeidsgiverperioder.isEmpty()) {
        null
    } else {
        Arbeidsgiverperiode(
            perioder = arbeidsgiverperioder,
            egenmeldinger = egenmeldingsperioder,
            redusertLoennIAgp = fullLønnIArbeidsgiverPerioden?.convert(),
        )
    }

fun InntektsmeldingGammeltFormat.convertInntekt(): Inntekt? =
    if (inntekt == null) {
        null
    } else {
        Inntekt(
            beloep = inntekt.beregnetInntekt,
            inntektsdato =
                inntektsdato
                    ?: bestemmendeFraværsdag
                    ?: bestemmendeFravaersdag(
                        arbeidsgiverperioder = arbeidsgiverperioder,
                        sykefravaersperioder = fraværsperioder,
                    ),
            naturalytelser = naturalytelser?.map { it.convert() }.orEmpty(),
            endringAarsaker = listOfNotNull(inntekt.endringÅrsak?.convert()),
        )
    }

fun RefusjonGammeltFormat.convert(): Refusjon? =
    // (refusjonPrMnd == null) bør ikke skje, men deprecated nullable-kode åpner for ugyldige data.
    if (!utbetalerHeleEllerDeler || refusjonPrMnd == null) {
        null
    } else {
        Refusjon(
            beloepPerMaaned = refusjonPrMnd,
            endringer = refusjonEndringer?.mapNotNull { it.convert() }.orEmpty(),
            sluttdato = refusjonOpphører,
        )
    }

fun BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.convert(): RedusertLoennIAgp.Begrunnelse = RedusertLoennIAgp.Begrunnelse.valueOf(name)

fun NaturalytelseGammeltFormat.convert(): Naturalytelse =
    Naturalytelse(
        naturalytelse = naturalytelse.name.let(Naturalytelse.Kode::valueOf),
        verdiBeloep = beløp,
        sluttdato = dato,
    )

fun InntektEndringAarsakGammeltFormat.convert(): InntektEndringAarsak =
    when (this) {
        is BonusGammeltFormat -> Bonus
        is FeilregistrertGammeltFormat -> Feilregistrert
        is FerieGammeltFormat -> Ferie(ferier = liste)
        is FerietrekkGammeltFormat -> Ferietrekk
        is NyansattGammeltFormat -> Nyansatt
        is NyStillingGammeltFormat -> NyStilling(gjelderFra = gjelderFra)
        is NyStillingsprosentGammeltFormat -> NyStillingsprosent(gjelderFra = gjelderFra)
        is PermisjonGammeltFormat -> Permisjon(permisjoner = liste)
        is PermitteringGammeltFormat -> Permittering(permitteringer = liste)
        is SykefravaerGammeltFormat -> Sykefravaer(sykefravaer = liste)
        is TariffendringGammeltFormat -> Tariffendring(gjelderFra = gjelderFra, bleKjent = bleKjent)
        is VarigLonnsendringGammeltFormat -> VarigLoennsendring(gjelderFra = gjelderFra)
    }

private fun FullLoennIAgpGammeltFormat.convert(): RedusertLoennIAgp? =
    if (utbetalerFullLønn || utbetalt == null || begrunnelse == null) {
        null
    } else {
        RedusertLoennIAgp(
            beloep = utbetalt,
            begrunnelse = begrunnelse.convert(),
        )
    }

private fun RefusjonEndringGammeltFormat.convert(): RefusjonEndring? =
    if (beløp == null || dato == null) {
        null
    } else {
        RefusjonEndring(beløp, dato)
    }
