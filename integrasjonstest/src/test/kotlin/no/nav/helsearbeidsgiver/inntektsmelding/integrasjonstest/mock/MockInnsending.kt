package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import java.time.LocalDate

fun mockInnsending(): Innsending =
    Innsending(
        orgnrUnderenhet = "orgnr-bål",
        identitetsnummer = "fnr-fredrik",
        behandlingsdager = listOf(LocalDate.now().plusDays(5)),
        egenmeldingsperioder = listOf(
            Periode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(2)
            )
        ),
        arbeidsgiverperioder = emptyList(),
        bestemmendeFraværsdag = LocalDate.now(),
        fraværsperioder = emptyList(),
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = 32100.0,
            endringÅrsak = null,
            manueltKorrigert = false
        ),
        fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
            utbetalerFullLønn = true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert
        ),
        refusjon = Refusjon(
            utbetalerHeleEllerDeler = true,
            refusjonPrMnd = 200.0,
            refusjonOpphører = LocalDate.now()
        ),
        naturalytelser = listOf(
            Naturalytelse(
                naturalytelse = NaturalytelseKode.KOSTDOEGN,
                dato = LocalDate.now(),
                beløp = 300.0
            )
        ),
        årsakInnsending = AarsakInnsending.ENDRING,
        bekreftOpplysninger = true
    )
