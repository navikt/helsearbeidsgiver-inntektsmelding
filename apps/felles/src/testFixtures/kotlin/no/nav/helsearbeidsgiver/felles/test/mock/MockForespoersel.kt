package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
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
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder = listOf(2.januar til 31.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 1.januar,
                Orgnr.genererGyldig() to 5.januar,
            ),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        erBegrenset = false,
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

fun Forespoersel.utenPaakrevdAGP(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                arbeidsgiverperiode =
                    ForespurtData.Arbeidsgiverperiode(
                        paakrevd = false,
                    ),
            ),
    )

fun Forespoersel.utenPaakrevdInntekt(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                inntekt =
                    ForespurtData.Inntekt(
                        paakrevd = false,
                        forslag = null,
                    ),
            ),
    )

fun Forespoersel.utenPaakrevdRefusjon(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                refusjon =
                    ForespurtData.Refusjon(
                        paakrevd = false,
                        forslag =
                            ForslagRefusjon(
                                perioder = emptyList(),
                                opphoersdato = null,
                            ),
                    ),
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
