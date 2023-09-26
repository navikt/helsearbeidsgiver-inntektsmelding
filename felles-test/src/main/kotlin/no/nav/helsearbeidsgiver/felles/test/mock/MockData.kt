package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForrigeInntekt
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = ForespurtData.Arbeidsgiverperiode(
            paakrevd = true
        ),
        inntekt = ForespurtData.Inntekt(
            paakrevd = true,
            forslag = ForslagInntekt.Grunnlag(forrigeInntekt = null)
        ),
        refusjon = ForespurtData.Refusjon(
            paakrevd = true,
            forslag = ForslagRefusjon(
                perioder = listOf(
                    ForslagRefusjon.Periode(
                        fom = 10.januar(2017),
                        beloep = 10.48
                    ),
                    ForslagRefusjon.Periode(
                        fom = 2.februar(2017),
                        beloep = 98.26
                    )
                ),
                opphoersdato = 26.februar(2017)
            )
        )
    )

fun mockForespurtDataMedForrigeInntekt(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = ForespurtData.Arbeidsgiverperiode(
            paakrevd = false
        ),
        inntekt = ForespurtData.Inntekt(
            paakrevd = true,
            forslag = ForslagInntekt.Grunnlag(
                forrigeInntekt = ForrigeInntekt(
                    skjæringstidspunkt = 1.januar.minusYears(1),
                    kilde = "INNTEKTSMELDING",
                    beløp = 10000.0
                )
            )
        ),
        refusjon = ForespurtData.Refusjon(
            paakrevd = true,
            forslag = ForslagRefusjon(
                perioder = listOf(
                    ForslagRefusjon.Periode(
                        fom = 10.januar(2017),
                        beloep = 10.48
                    ),
                    ForslagRefusjon.Periode(
                        fom = 2.februar(2017),
                        beloep = 98.26
                    )
                ),
                opphoersdato = 26.februar(2017)
            )
        )
    )

fun mockForespurtDataMedFastsattInntekt(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = ForespurtData.Arbeidsgiverperiode(
            paakrevd = true
        ),
        inntekt = ForespurtData.Inntekt(
            paakrevd = false,
            forslag = ForslagInntekt.Fastsatt(
                fastsattInntekt = 31415.92
            )
        ),
        refusjon = ForespurtData.Refusjon(
            paakrevd = true,
            forslag = ForslagRefusjon(
                perioder = listOf(
                    ForslagRefusjon.Periode(
                        fom = 1.januar,
                        beloep = 31415.92
                    ),
                    ForslagRefusjon.Periode(
                        fom = 15.januar,
                        beloep = 3.14
                    )
                ),
                opphoersdato = null
            )
        )
    )

fun mockTrengerInntekt(): TrengerInntekt =
    TrengerInntekt(
        type = ForespoerselType.KOMPLETT,
        orgnr = "123",
        fnr = "456",
        skjaeringstidspunkt = 11.januar(2018),
        sykmeldingsperioder = listOf(2.januar til 31.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        forespurtData = mockForespurtData(),
        erBesvart = false
    )
