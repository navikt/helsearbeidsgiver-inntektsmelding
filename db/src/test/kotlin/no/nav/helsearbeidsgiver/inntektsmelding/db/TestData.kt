package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.ÅrsakBeregnetInntektEndringKodeliste
import no.nav.helsearbeidsgiver.felles.inntektsmelding.ÅrsakInnsending
import java.time.LocalDate
import java.time.LocalDateTime

val INNTEKTSMELDING_DOKUMENT = InntektsmeldingDokument(
    "",
    "",
    "Ola Normann",
    "Norge AS",
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.now(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    beregnetInntekt = 502.0,
    beregnetInntektEndringÅrsak = ÅrsakBeregnetInntektEndringKodeliste.FeilInntekt,
    refusjon = Refusjon(
        500.0,
        refusjonOpphører = LocalDate.now(),
        refusjonEndringer = emptyList()
    ),
    årsakInnsending = ÅrsakInnsending.Ny,
    tidspunkt = LocalDateTime.now(),
    fullLønnIArbeidsgiverPerioden = FullLønnIArbeidsgiverPerioden(
        utbetalerFullLønn = true,
        begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent,
        utbetalt = 500.0
    ),
    identitetsnummerInnsender = "123"
)
