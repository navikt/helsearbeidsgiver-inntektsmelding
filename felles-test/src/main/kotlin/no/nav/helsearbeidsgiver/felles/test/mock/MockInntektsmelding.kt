package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.test.date.desember
import java.time.ZonedDateTime

private val dag = 24.desember(2022)
private const val INNTEKT = 25_000.0

fun mockInntektsmelding(): Inntektsmelding =
    Inntektsmelding(
        orgnrUnderenhet = "123456789",
        identitetsnummer = "12345678901",
        fulltNavn = "Ola Normann",
        virksomhetNavn = "Norge AS",
        behandlingsdager = listOf(dag),
        egenmeldingsperioder = listOf(
            Periode(dag, dag.plusDays(2)),
            Periode(dag.plusDays(3), dag.plusDays(4))
        ),
        beregnetInntekt = INNTEKT,
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = INNTEKT,
            endringÅrsak = Tariffendring(dag, dag),
            manueltKorrigert = false
        ),
        fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
            utbetalerFullLønn = true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent,
            utbetalt = 10_000.0
        ),
        refusjon = Refusjon(
            utbetalerHeleEllerDeler = true,
            refusjonPrMnd = INNTEKT,
            refusjonOpphører = dag.plusDays(3),
            refusjonEndringer = listOf(
                RefusjonEndring(140.0, dag.minusDays(4)),
                RefusjonEndring(150.0, dag.minusDays(5)),
                RefusjonEndring(160.0, dag.minusDays(6))
            )
        ),
        naturalytelser = listOf(
            Naturalytelse(
                NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0
            ),
            Naturalytelse(
                NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0
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
        årsakInnsending = AarsakInnsending.NY,
        innsenderNavn = "Snill Arbeidsgiver",
        telefonnummer = "22222222"
    )

fun mockDelvisInntektsmeldingDokument() = mockInntektsmelding().copy(
    // Nullstill alle unødige felter, sett inntekt og refusjon
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    fullLønnIArbeidsgiverPerioden = null,
    naturalytelser = null,
    inntekt = Inntekt(
        bekreftet = true,
        beregnetInntekt = INNTEKT,
        endringÅrsak = Tariffendring(dag, dag),
        manueltKorrigert = false
    ),
    refusjon = Refusjon(
        utbetalerHeleEllerDeler = true,
        refusjonPrMnd = INNTEKT,
        refusjonOpphører = dag.plusDays(3),
        refusjonEndringer = listOf(
            RefusjonEndring(140.0, dag.minusDays(4)),
            RefusjonEndring(150.0, dag.minusDays(5)),
            RefusjonEndring(160.0, dag.minusDays(6))
        )
    ),
    forespurtData = listOf("inntekt", "refusjon")
)
