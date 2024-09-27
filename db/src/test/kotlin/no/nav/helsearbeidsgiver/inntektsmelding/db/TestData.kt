package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

val INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT =
    Inntektsmelding(
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
        inntekt = null,
        refusjon =
            Refusjon(
                true,
                500.0,
                refusjonOpphører = LocalDate.now(),
                refusjonEndringer = emptyList(),
            ),
        årsakInnsending = AarsakInnsending.NY,
        tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
        fullLønnIArbeidsgiverPerioden =
            FullLoennIArbeidsgiverPerioden(
                utbetalerFullLønn = true,
                begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent,
                utbetalt = 500.0,
            ),
        innsenderNavn = "Fido",
        vedtaksperiodeId = UUID.randomUUID(),
    )
