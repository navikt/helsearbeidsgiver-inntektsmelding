package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
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
