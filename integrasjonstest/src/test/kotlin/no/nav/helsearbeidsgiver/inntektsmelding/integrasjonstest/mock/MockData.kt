package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.time.LocalDate
import java.util.UUID

fun mockInnsending(): Innsending =
    Innsending(
        orgnrUnderenhet = "orgnr-bål",
        identitetsnummer = "fnr-fredrik",
        behandlingsdager = listOf(LocalDate.now().plusDays(5)),
        egenmeldingsperioder = listOf(
            Periode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(2)
            )
        ),
        arbeidsgiverperioder = emptyList(),
        bestemmendeFraværsdag = LocalDate.now(),
        fraværsperioder = emptyList(),
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = 32100.0,
            endringÅrsak = null,
            manueltKorrigert = false
        ),
        fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
            utbetalerFullLønn = true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert
        ),
        refusjon = Refusjon(
            utbetalerHeleEllerDeler = true,
            refusjonPrMnd = 200.0,
            refusjonOpphører = LocalDate.now()
        ),
        naturalytelser = listOf(
            Naturalytelse(
                naturalytelse = NaturalytelseKode.KOSTDOEGN,
                dato = LocalDate.now(),
                beløp = 300.0
            )
        ),
        årsakInnsending = AarsakInnsending.ENDRING,
        bekreftOpplysninger = true
    )

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess =
    ForespoerselSvar.Suksess(
        type = ForespoerselType.KOMPLETT,
        orgnr = "767434313",
        fnr = "24120012345",
        skjaeringstidspunkt = 11.januar(2018),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        vedtaksperiodeId = UUID.randomUUID()
    )
