package no.nav.hag.simba.kontrakt.resultat.soeknad.test

import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.resultat.soeknad.ForespoerselMedId
import java.util.UUID

fun mockForespoerselMedId(soeknad: Soeknad.Arbeidstaker): ForespoerselMedId =
    ForespoerselMedId(
        forespoerselId = UUID.randomUUID(),
        forespoersel =
            mockForespoersel().copy(
                vedtaksperiodeId = soeknad.vedtaksperiodeId,
                sykmeldingsperioder = listOf(soeknad.sykmeldingsperiode),
                egenmeldingsperioder = soeknad.egenmeldingerFraSykmelding,
            ),
    )
