package no.nav.hag.simba.kontrakt.domene.soeknad.test

import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import java.util.UUID

fun mockSoeknadArbeidstaker(): Soeknad.Arbeidstaker =
    Soeknad.Arbeidstaker(
        soeknadId = UUID.randomUUID(),
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperiode = 4.oktober til 26.oktober,
        egenmeldingerFraSykmelding = listOf(2.oktober til 3.oktober),
        erGradert = false,
    )

fun mockSoeknadBehandlingsdager(): Soeknad.Behandlingsdager =
    Soeknad.Behandlingsdager(
        soeknadId = UUID.randomUUID(),
        sykmeldingsperiode = 1.november til 30.november,
        behandlingsdager =
            setOf(
                2.november,
                9.november,
                16.november,
                23.november,
                30.november,
            ),
    )
