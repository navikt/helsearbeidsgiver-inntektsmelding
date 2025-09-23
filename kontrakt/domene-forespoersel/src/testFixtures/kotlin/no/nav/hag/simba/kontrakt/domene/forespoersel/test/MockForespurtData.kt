package no.nav.hag.simba.kontrakt.domene.forespoersel.test

import no.nav.hag.simba.kontrakt.domene.forespoersel.ForespurtData
import no.nav.hag.simba.kontrakt.domene.forespoersel.ForrigeInntekt
import no.nav.hag.simba.kontrakt.domene.forespoersel.ForslagInntekt
import no.nav.hag.simba.kontrakt.domene.forespoersel.ForslagRefusjon
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            ForespurtData.Arbeidsgiverperiode(
                paakrevd = true,
            ),
        inntekt =
            ForespurtData.Inntekt(
                paakrevd = true,
                forslag = null,
            ),
        refusjon =
            ForespurtData.Refusjon(
                paakrevd = true,
                forslag = mockForespurtDataForslagRefusjon(),
            ),
    )

fun mockForespurtDataMedTomtInntektForslag(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            ForespurtData.Arbeidsgiverperiode(
                paakrevd = true,
            ),
        inntekt =
            ForespurtData.Inntekt(
                paakrevd = true,
                forslag = ForslagInntekt(forrigeInntekt = null),
            ),
        refusjon =
            ForespurtData.Refusjon(
                paakrevd = true,
                forslag = mockForespurtDataForslagRefusjon(),
            ),
    )

fun mockForespurtDataMedForrigeInntekt(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            ForespurtData.Arbeidsgiverperiode(
                paakrevd = false,
            ),
        inntekt =
            ForespurtData.Inntekt(
                paakrevd = true,
                forslag =
                    ForslagInntekt(
                        forrigeInntekt =
                            ForrigeInntekt(
                                skjæringstidspunkt = 1.januar.minusYears(1),
                                kilde = "INNTEKTSMELDING",
                                beløp = 10000.0,
                            ),
                    ),
            ),
        refusjon =
            ForespurtData.Refusjon(
                paakrevd = true,
                forslag = mockForespurtDataForslagRefusjon(),
            ),
    )

private fun mockForespurtDataForslagRefusjon(): ForslagRefusjon =
    ForslagRefusjon(
        perioder =
            listOf(
                ForslagRefusjon.Periode(
                    fom = 10.januar(2017),
                    beloep = 10.48,
                ),
                ForslagRefusjon.Periode(
                    fom = 2.februar(2017),
                    beloep = 98.26,
                ),
            ),
        opphoersdato = 26.februar(2017),
    )
