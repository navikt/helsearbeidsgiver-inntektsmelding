package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
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
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT
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
