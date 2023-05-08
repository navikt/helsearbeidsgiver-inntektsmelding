package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import java.time.LocalDate
import java.time.ZonedDateTime

val INNTEKTSMELDING_DOKUMENT = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument(
    "",
    "",
    "Ola Normann",
    "Norge AS",
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.now(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    beregnetInntekt = 502.0.toBigDecimal(),
    Inntekt(
        bekreftet = true,
        beregnetInntekt = 502.0.toBigDecimal(),
        endringÅrsak = null,
        manueltKorrigert = false
    ),
    refusjon = Refusjon(
        true,
        500.0.toBigDecimal(),
        refusjonOpphører = LocalDate.now(),
        refusjonEndringer = emptyList()
    ),
    årsakInnsending = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.NY,
    tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
    fullLønnIArbeidsgiverPerioden = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden(
        utbetalerFullLønn = true,
        begrunnelse = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT,
        utbetalt = 500.0.toBigDecimal()
    ),
    identitetsnummerInnsender = "123"
)

val INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT = INNTEKTSMELDING_DOKUMENT.copy(inntekt = null)
