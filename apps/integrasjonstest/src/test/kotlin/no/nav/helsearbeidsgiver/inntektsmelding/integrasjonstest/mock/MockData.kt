package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.ForespoerselFraBro
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.test.mockForespoerselFraBro
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoerselSvarSuksess(): ForespoerselFraBro {
    val orgnr = Orgnr.genererGyldig()
    return mockForespoerselFraBro().copy(
        orgnr = orgnr,
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 1.januar,
                Orgnr.genererGyldig() to 11.januar,
            ),
    )
}

fun mockForespoerselListeSvarResultat(
    vedtaksperiodeId1: UUID,
    vedtaksperiodeId2: UUID,
): List<ForespoerselFraBro> =
    mockForespoerselFraBro().let {
        listOf(
            it.copy(vedtaksperiodeId = vedtaksperiodeId1),
            it.copy(vedtaksperiodeId = vedtaksperiodeId2),
        )
    }
