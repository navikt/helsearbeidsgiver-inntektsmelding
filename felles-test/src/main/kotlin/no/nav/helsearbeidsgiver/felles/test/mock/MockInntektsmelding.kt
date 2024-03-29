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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending as AarsakInnsendingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt as InntektV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse as NaturalytelseV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon as RefusjonV1

private val dag = 24.desember(2022)
private const val INNTEKT = 25_000.0

fun mockInntektsmeldingV1(): InntektsmeldingV1 =
    InntektsmeldingV1(
        id = UUID.randomUUID(),
        type = InntektsmeldingV1.Type.FORESPURT,
        sykmeldt = Sykmeldt(
            fnr = "16054577777",
            navn = "Skummel Bolle"
        ),
        avsender = Avsender(
            "000444000",
            "Skumle bakverk A/S",
            "07072288888",
            "Nifs Krumkake",
            "44553399"
        ),
        sykmeldingsperioder = listOf(
            Periode(
                fom = 5.oktober,
                tom = 15.oktober
            ),
            Periode(
                fom = 20.oktober,
                tom = 3.november
            )
        ),
        agp = Arbeidsgiverperiode(
            listOf(
                Periode(
                    fom = 5.oktober,
                    tom = 15.oktober
                ),
                Periode(
                    fom = 20.oktober,
                    tom = 22.oktober
                )
            ),
            listOf(
                Periode(
                    fom = 28.september,
                    tom = 28.september
                ),
                Periode(
                    fom = 30.september,
                    tom = 30.september
                )
            ),
            RedusertLoennIAgp(
                beloep = 300.3,
                begrunnelse = RedusertLoennIAgp.Begrunnelse.FerieEllerAvspasering
            )
        ),
        inntekt = InntektV1(
            beloep = 544.6,
            inntektsdato = 28.september,
            naturalytelser = listOf(
                NaturalytelseV1(
                    naturalytelse = NaturalytelseV1.Kode.BEDRIFTSBARNEHAGEPLASS,
                    verdiBeloep = 52.5,
                    sluttdato = 10.oktober
                ),
                NaturalytelseV1(
                    naturalytelse = NaturalytelseV1.Kode.BIL,
                    verdiBeloep = 434.0,
                    sluttdato = 12.oktober
                )
            ),
            endringAarsak = NyStillingsprosent(
                gjelderFra = 16.oktober
            )
        ),
        refusjon = RefusjonV1(
            beloepPerMaaned = 150.2,
            endringer = listOf(),
            sluttdato = 31.oktober
        ),
        aarsakInnsending = AarsakInnsendingV1.Endring,
        mottatt = 14.mars.kl(14, 41, 42, 0).atOffset(ZoneOffset.ofHours(1))
    )

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
