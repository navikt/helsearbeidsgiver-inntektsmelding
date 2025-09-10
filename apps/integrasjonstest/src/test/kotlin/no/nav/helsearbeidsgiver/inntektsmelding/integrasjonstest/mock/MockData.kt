package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.hag.simba.utils.felles.domene.ForespoerselFraBro
import no.nav.hag.simba.utils.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoerselSvarSuksess(): ForespoerselFraBro {
    val orgnr = Orgnr.genererGyldig()
    return ForespoerselFraBro(
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = UUID.randomUUID(),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 1.januar,
                Orgnr.genererGyldig() to 11.januar,
            ),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        erBegrenset = false,
    )
}

fun mockForespoerselListeSvarResultat(
    vedtaksperiodeId1: UUID,
    vedtaksperiodeId2: UUID,
): List<ForespoerselFraBro> {
    val orgnr = Orgnr.genererGyldig()
    val forespoersel =
        ForespoerselFraBro(
            orgnr = orgnr,
            fnr = Fnr.genererGyldig(),
            forespoerselId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeId1,
            egenmeldingsperioder = listOf(1.januar til 1.januar),
            sykmeldingsperioder = listOf(2.januar til 16.januar),
            bestemmendeFravaersdager =
                mapOf(
                    orgnr to 1.januar,
                ),
            forespurtData = mockForespurtData(),
            erBesvart = false,
            erBegrenset = false,
        )
    return listOf(forespoersel, forespoersel.copy(vedtaksperiodeId = vedtaksperiodeId2))
}
