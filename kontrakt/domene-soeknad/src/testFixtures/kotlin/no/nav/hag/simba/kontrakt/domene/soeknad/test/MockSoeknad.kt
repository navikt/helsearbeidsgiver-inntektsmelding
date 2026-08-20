package no.nav.hag.simba.kontrakt.domene.soeknad.test

import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import java.util.UUID

fun mockSoeknadArbeidstaker(): Soeknad.Arbeidstaker =
    Soeknad.Arbeidstaker(
        soeknadId = UUID.randomUUID(),
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingId = UUID.randomUUID(),
        fom = 2.oktober,
        tom = 26.oktober,
        erGradert = false,
        egenmeldingerFraSykmelding = setOf(2.oktober, 3.oktober),
    )

fun mockSoeknadBehandlingsdager(): Soeknad.Behandlingsdager =
    Soeknad.Behandlingsdager(
        soeknadId = UUID.randomUUID(),
        sykmeldingId = UUID.randomUUID(),
        fom = 1.november,
        tom = 30.november,
        behandlingsdager =
            setOf(
                2.november,
                9.november,
                16.november,
                23.november,
                30.november,
            ),
    )
