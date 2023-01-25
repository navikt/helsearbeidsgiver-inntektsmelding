package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.test.date.desember
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.date.november
import no.nav.helsearbeidsgiver.felles.test.date.oktober
import no.nav.helsearbeidsgiver.felles.til

fun mockForespurtDataListe(): List<ForespurtData> =
    listOf(
        ForespurtData.ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        ForespurtData.Inntekt(
            forslag = ForslagInntekt(
                beregningsmåneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        ForespurtData.Refusjon
    )

fun mockForespurtDataMedFastsattInntektListe(): List<ForespurtData> =
    listOf(
        ForespurtData.ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        ForespurtData.FastsattInntekt(
            fastsattInntekt = 31415.92
        ),
        ForespurtData.Refusjon
    )
