package no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test

import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.ForespurtData
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.ForslagRefusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
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
