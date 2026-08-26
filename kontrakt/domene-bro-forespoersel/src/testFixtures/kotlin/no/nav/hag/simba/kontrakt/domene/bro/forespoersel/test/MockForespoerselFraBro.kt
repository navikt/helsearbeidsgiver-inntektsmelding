package no.nav.hag.simba.kontrakt.domene.bro.forespoersel.test

import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.ForespoerselFraBro
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoerselFraBro(): ForespoerselFraBro {
    val orgnr = Orgnr.genererGyldig()
    return ForespoerselFraBro(
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder = listOf(2.januar til 31.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        erBegrenset = false,
    )
}

fun mockForespoerselFraBro(forespoersel: Forespoersel): ForespoerselFraBro =
    ForespoerselFraBro(
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
        forespurtData = forespoersel.forespurtData,
        erBesvart = forespoersel.erBesvart,
        erBegrenset = forespoersel.erBegrenset,
    )
