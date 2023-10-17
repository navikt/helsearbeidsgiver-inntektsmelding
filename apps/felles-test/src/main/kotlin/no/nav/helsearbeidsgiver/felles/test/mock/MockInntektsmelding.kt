package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Tariffendring
import no.nav.helsearbeidsgiver.utils.test.date.desember
import java.time.ZonedDateTime

private val dag = 24.desember(2022)
private val inntekt = 25_000.0

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
        beregnetInntekt = inntekt,
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = inntekt,
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
            refusjonPrMnd = inntekt,
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
        beregnetInntekt = inntekt,
        endringÅrsak = Tariffendring(dag, dag),
        manueltKorrigert = false
    ),
    refusjon = Refusjon(
        utbetalerHeleEllerDeler = true,
        refusjonPrMnd = inntekt,
        refusjonOpphører = dag.plusDays(3),
        refusjonEndringer = listOf(
            RefusjonEndring(140.0, dag.minusDays(4)),
            RefusjonEndring(150.0, dag.minusDays(5)),
            RefusjonEndring(160.0, dag.minusDays(6))
        )
    ),
    forespurtData = listOf("inntekt", "refusjon")
)
