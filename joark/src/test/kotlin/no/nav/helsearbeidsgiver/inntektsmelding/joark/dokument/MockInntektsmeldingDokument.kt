package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import java.time.LocalDate
import java.time.ZonedDateTime

fun MockInntektsmeldingDokument(dag: LocalDate = LocalDate.of(2022, 12, 24)): InntektsmeldingDokument =
    InntektsmeldingDokument(
        orgnrUnderenhet = "123456789",
        identitetsnummer = "12345678901",
        fulltNavn = "Ola Normann",
        virksomhetNavn = "Norge AS",
        behandlingsdager = listOf(dag),
        egenmeldingsperioder = listOf(
            Periode(dag, dag.plusDays(2)),
            Periode(dag.plusDays(3), dag.plusDays(4))
        ),
        beregnetInntekt = 25000.0.toBigDecimal(),
        inntekt = Inntekt(true, 25000.0.toBigDecimal(), Tariffendring(dag, dag), false),
        fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
            true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT,
            utbetalt = 10000.toBigDecimal()
        ),
        refusjon = Refusjon(
            true,
            25000.0.toBigDecimal(),
            dag.plusDays(3),
            listOf(
                RefusjonEndring(140.0.toBigDecimal(), dag.minusDays(4)),
                RefusjonEndring(150.0.toBigDecimal(), dag.minusDays(5)),
                RefusjonEndring(160.0.toBigDecimal(), dag.minusDays(6))
            )
        ),
        naturalytelser = listOf(
            Naturalytelse(
                NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0.toBigDecimal()
            ),
            Naturalytelse(
                NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0.toBigDecimal()
            )
        ),
        fraværsperioder = listOf(
            Periode(dag, dag.plusDays(55)),
            Periode(dag, dag.plusDays(22)),
            Periode(dag, dag.plusDays(32))
        ),
        arbeidsgiverperioder = listOf(
            Periode(dag, dag.plusDays(30)),
            Periode(dag, dag.plusDays(40)),
            Periode(dag, dag.plusDays(40))
        ),
        bestemmendeFraværsdag = dag.plusDays(90),
        tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
        årsakInnsending = ÅrsakInnsending.NY,
        identitetsnummerInnsender = "123123123123123"
    )
