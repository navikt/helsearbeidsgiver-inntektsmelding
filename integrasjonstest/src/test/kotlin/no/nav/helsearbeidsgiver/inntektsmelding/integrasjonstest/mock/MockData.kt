package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselType
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess {
    val orgnr = Orgnr.genererGyldig().verdi
    return ForespoerselSvar.Suksess(
        type = ForespoerselType.KOMPLETT,
        orgnr = orgnr,
        fnr = Fnr.genererGyldig().verdi,
        vedtaksperiodeId = UUID.randomUUID(),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        skjaeringstidspunkt = 11.januar,
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 1.januar,
                "343999567" to 11.januar,
            ),
        forespurtData = mockForespurtData(),
        erBesvart = false,
    )
}

fun mockForespoerselListeSvarResultat(
    vedtaksperiodeId1: UUID,
    vedtaksperiodeId2: UUID,
): List<ForespoerselListeSvar.Forespoersel> {
    val orgnr = Orgnr.genererGyldig()
    val forespoersel =
        ForespoerselListeSvar.Forespoersel(
            type = ForespoerselType.KOMPLETT,
            orgnr = orgnr,
            fnr = Fnr.genererGyldig().verdi,
            forespoerselId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeId1,
            egenmeldingsperioder = listOf(1.januar til 1.januar),
            sykmeldingsperioder = listOf(2.januar til 16.januar),
            skjaeringstidspunkt = 11.januar,
            bestemmendeFravaersdager =
                mapOf(
                    orgnr to 1.januar,
                ),
            forespurtData = mockForespurtData(),
            erBesvart = false,
        )
    return listOf(forespoersel, forespoersel.copy(vedtaksperiodeId = vedtaksperiodeId2))
}
