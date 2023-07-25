package no.nav.helsearbeidsgiver.felles.test.mock

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
import no.nav.helsearbeidsgiver.utils.test.date.desember
import java.time.ZonedDateTime

private val dag = 24.desember(2022)
private val inntekt = 25_000.0.toBigDecimal()

fun mockInntektsmeldingDokument(): InntektsmeldingDokument =
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
        beregnetInntekt = inntekt,
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = inntekt,
            endringÅrsak = Tariffendring(dag, dag),
            manueltKorrigert = false
        ),
        fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
            utbetalerFullLønn = true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT,
            utbetalt = 10_000.toBigDecimal()
        ),
        refusjon = Refusjon(
            utbetalerHeleEllerDeler = true,
            refusjonPrMnd = inntekt,
            refusjonOpphører = dag.plusDays(3),
            refusjonEndringer = listOf(
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
