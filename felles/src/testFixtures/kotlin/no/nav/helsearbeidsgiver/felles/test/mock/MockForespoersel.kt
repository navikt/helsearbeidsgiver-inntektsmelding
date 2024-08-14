package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselType
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.domene.ForrigeInntekt
import no.nav.helsearbeidsgiver.felles.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoersel(): Forespoersel {
    val orgnr = Orgnr.genererGyldig()
    return Forespoersel(
        type = ForespoerselType.KOMPLETT,
        orgnr = orgnr.verdi,
        fnr = Fnr.genererGyldig().verdi,
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder = listOf(2.januar til 31.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        bestemmendeFravaersdager =
            mapOf(
                orgnr.verdi to 1.januar,
                Orgnr.genererGyldig().verdi to 5.januar,
            ),
        forespurtData = mockForespurtData(),
        erBesvart = false,
    )
}

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            ForespurtData.Arbeidsgiverperiode(
                paakrevd = true,
            ),
        inntekt =
            ForespurtData.Inntekt(
                paakrevd = true,
                forslag = ForslagInntekt.Grunnlag(forrigeInntekt = null),
            ),
        refusjon =
            ForespurtData.Refusjon(
                paakrevd = true,
                forslag =
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
                    ),
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
                    ForslagInntekt.Grunnlag(
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
                forslag =
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
                    ),
            ),
    )

fun mockForespurtDataMedFastsattInntekt(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            ForespurtData.Arbeidsgiverperiode(
                paakrevd = true,
            ),
        inntekt =
            ForespurtData.Inntekt(
                paakrevd = false,
                forslag =
                    ForslagInntekt.Fastsatt(
                        fastsattInntekt = 31415.92,
                    ),
            ),
        refusjon =
            ForespurtData.Refusjon(
                paakrevd = true,
                forslag =
                    ForslagRefusjon(
                        perioder =
                            listOf(
                                ForslagRefusjon.Periode(
                                    fom = 1.januar,
                                    beloep = 31415.92,
                                ),
                                ForslagRefusjon.Periode(
                                    fom = 15.januar,
                                    beloep = 3.14,
                                ),
                            ),
                        opphoersdato = null,
                    ),
            ),
    )
