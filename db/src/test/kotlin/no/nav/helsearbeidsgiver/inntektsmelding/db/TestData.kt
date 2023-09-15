package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

val INNTEKTSMELDING_DOKUMENT = InntektsmeldingDokument(
    orgnrUnderenhet = "",
    identitetsnummer = "",
    fulltNavn = "Ola Normann",
    virksomhetNavn = "Norge AS",
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.now(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    beregnetInntekt = 502.0.toBigDecimal(),
    inntekt = Inntekt(
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
    årsakInnsending = ÅrsakInnsending.NY,
    tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
    fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
        utbetalerFullLønn = true,
        begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT,
        utbetalt = 500.0.toBigDecimal()
    ),
    innsenderNavn = "Fido"
)

val EKSTERN_INNTEKTSMELDING_DOKUMENT = EksternInntektsmelding(
    "AltinnPortal",
    "1.63",
    "AR123456",
    LocalDateTime.now()
)

val INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT = INNTEKTSMELDING_DOKUMENT.copy(inntekt = null)

val INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA = INNTEKTSMELDING_DOKUMENT.copy(forespurtData = emptyList())
val INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA = INNTEKTSMELDING_DOKUMENT.copy(forespurtData = listOf("inntekt", "refusjon"))
