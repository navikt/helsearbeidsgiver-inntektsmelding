package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

val INNTEKTSMELDING_DOKUMENT = Inntektsmelding(
    orgnrUnderenhet = "",
    identitetsnummer = "",
    fulltNavn = "Ola Normann",
    virksomhetNavn = "Norge AS",
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.now(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    beregnetInntekt = 502.0,
    inntekt = Inntekt(
        bekreftet = true,
        beregnetInntekt = 502.0,
        endringÅrsak = null,
        manueltKorrigert = false
    ),
    refusjon = Refusjon(
        true,
        500.0,
        refusjonOpphører = LocalDate.now(),
        refusjonEndringer = emptyList()
    ),
    årsakInnsending = AarsakInnsending.NY,
    tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
    fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
        utbetalerFullLønn = true,
        begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent,
        utbetalt = 500.0
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
