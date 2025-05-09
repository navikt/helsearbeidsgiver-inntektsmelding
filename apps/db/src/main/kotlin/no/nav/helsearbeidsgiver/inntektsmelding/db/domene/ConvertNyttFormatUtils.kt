@file:Suppress("DEPRECATION")

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
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
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

fun Inntektsmelding.convert(): InntektsmeldingGammeltFormat =
    InntektsmeldingGammeltFormat(
        vedtaksperiodeId = vedtaksperiodeId,
        orgnrUnderenhet = avsender.orgnr.verdi,
        identitetsnummer = sykmeldt.fnr.verdi,
        fraværsperioder = sykmeldingsperioder,
        egenmeldingsperioder = agp?.egenmeldinger.orEmpty(),
        arbeidsgiverperioder = agp?.perioder.orEmpty(),
        fullLønnIArbeidsgiverPerioden =
            agp?.redusertLoennIAgp?.convert().orDefault(
                FullLoennIAgpGammeltFormat(
                    utbetalerFullLønn = true,
                    begrunnelse = null,
                    utbetalt = null,
                ),
            ),
        inntekt = inntekt?.convert(),
        inntektsdato = inntekt?.inntektsdato,
        bestemmendeFraværsdag = null,
        naturalytelser = inntekt?.naturalytelser?.map { it.convert() }.orEmpty(),
        refusjon =
            refusjon?.convert().orDefault(
                RefusjonGammeltFormat(
                    utbetalerHeleEllerDeler = false,
                    refusjonPrMnd = null,
                    refusjonOpphører = null,
                    refusjonEndringer = null,
                ),
            ),
        innsenderNavn = avsender.navn,
        telefonnummer = avsender.tlf,
    )

fun Inntekt.convert(): InntektGammeltFormat =
    InntektGammeltFormat(
        bekreftet = true,
        beregnetInntekt = beloep,
        endringÅrsak = endringAarsaker.getOrNull(0)?.convert(),
        manueltKorrigert = endringAarsaker.isNotEmpty(),
    )

fun Refusjon.convert(): RefusjonGammeltFormat =
    RefusjonGammeltFormat(
        utbetalerHeleEllerDeler = true,
        refusjonPrMnd = beloepPerMaaned,
        refusjonEndringer = endringer.map { it.convert() },
    )

private fun InntektEndringAarsak.convert(): InntektEndringAarsakGammeltFormat =
    when (this) {
        is Bonus -> BonusGammeltFormat()
        is Feilregistrert -> FeilregistrertGammeltFormat
        is Ferie -> FerieGammeltFormat(liste = ferier)
        is Ferietrekk -> FerietrekkGammeltFormat
        is Nyansatt -> NyansattGammeltFormat
        is NyStilling -> NyStillingGammeltFormat(gjelderFra = gjelderFra)
        is NyStillingsprosent -> NyStillingsprosentGammeltFormat(gjelderFra = gjelderFra)
        is Permisjon -> PermisjonGammeltFormat(liste = permisjoner)
        is Permittering -> PermitteringGammeltFormat(liste = permitteringer)
        is Sykefravaer -> SykefravaerGammeltFormat(liste = sykefravaer)
        is Tariffendring -> TariffendringGammeltFormat(gjelderFra = gjelderFra, bleKjent = bleKjent)
        is VarigLoennsendring -> VarigLonnsendringGammeltFormat(gjelderFra = gjelderFra)
    }

private fun RedusertLoennIAgp.convert(): FullLoennIAgpGammeltFormat =
    FullLoennIAgpGammeltFormat(
        utbetalerFullLønn = false,
        begrunnelse = begrunnelse.convert(),
        utbetalt = beloep,
    )

private fun RedusertLoennIAgp.Begrunnelse.convert(): BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat =
    BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.valueOf(name)

private fun RefusjonEndring.convert(): RefusjonEndringGammeltFormat =
    RefusjonEndringGammeltFormat(
        beløp = beloep,
        dato = startdato,
    )

private fun Naturalytelse.convert(): NaturalytelseGammeltFormat =
    NaturalytelseGammeltFormat(
        naturalytelse = naturalytelse.name.let(NaturalytelseKodeGammeltFormat::valueOf),
        dato = sluttdato,
        beløp = verdiBeloep,
    )
