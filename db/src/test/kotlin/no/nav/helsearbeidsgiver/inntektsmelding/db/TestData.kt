package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakBeregnetInntektEndringKodeliste
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
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
