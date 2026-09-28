package no.nav.hag.simba.kontrakt.resultat.soeknad.test

import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
import no.nav.hag.simba.kontrakt.resultat.soeknad.ForespoerselMedId
import no.nav.hag.simba.kontrakt.resultat.soeknad.SoeknadMedForlengerId
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.util.UUID

object MockSoeknad {
    private val soeknadMedForespoersel1 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 8.mai til 12.mai,
            egenmeldingerFraSykmelding = emptyList(),
            erGradert = true,
        )

    // forlenges
    private val soeknadMedForespoersel2 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 18.mai til 31.mai,
            egenmeldingerFraSykmelding = listOf(15.mai til 17.mai),
        )
    private val soeknadMedForespoersel3 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 7.august til 28.august,
            egenmeldingerFraSykmelding = listOf(4.august til 6.august),
        )
    val forespoerselMedId1 = mockForespoerselMedId(soeknadMedForespoersel1)
    val forespoerselMedId2 =
        mockForespoerselMedId(soeknadMedForespoersel2).let {
            it.copy(
                forespoersel =
                    it.forespoersel.copy(
                        erBesvart = true,
                    ),
            )
        }
    val forespoerselMedId3 = mockForespoerselMedId(soeknadMedForespoersel3)
    val soeknadUtenForespoersel1 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 5.januar til 30.januar,
                    egenmeldingerFraSykmelding = listOf(2.januar til 3.januar),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenges
    val soeknadUtenForespoersel2 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 7.februar til 28.februar,
                    egenmeldingerFraSykmelding = listOf(6.februar til 6.februar),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenger forrige
    val soeknadUtenForespoersel3 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 1.mars til 17.mars,
                    egenmeldingerFraSykmelding = emptyList(),
                    erGradert = true,
                ),
            forlengerVedtaksperiodeId = soeknadUtenForespoersel2.soeknad.vedtaksperiodeId,
        )

    // forlenger forrige
    val soeknadUtenForespoersel4 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 18.mars til 2.april,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = soeknadUtenForespoersel3.forlengerVedtaksperiodeId,
        )

    // bryter forlengelse
    val soeknadUtenForespoersel5 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 4.april til 24.april,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenger forespoersel
    val soeknadUtenForespoersel6 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 1.juni til 10.juni,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = forespoerselMedId2.forespoersel.vedtaksperiodeId,
        )

    // bryter forlengelse forespoersel
    val soeknadUtenForespoersel7 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 12.juni til 20.juni,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenges ikke av egenmeldinger
    val soeknadUtenForespoersel8 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 22.juni til 29.juni,
                    egenmeldingerFraSykmelding = listOf(21.juni til 21.juni),
                ),
            forlengerVedtaksperiodeId = null,
        )
    val soeknadUtenForespoersel9 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 10.oktober til 20.oktober,
                    egenmeldingerFraSykmelding = listOf(7.oktober til 7.oktober),
                ),
            forlengerVedtaksperiodeId = null,
        )
    val soeknadBehandlingsdagerGruppe1 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 7.januar til 20.januar,
                behandlingsdager =
                    setOf(
                        7.januar,
                        17.januar,
                    ),
            ),
            // fullstendig overlapp med forrige
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 9.januar til 18.januar,
                behandlingsdager =
                    setOf(
                        10.januar,
                        // samme dag som forrige søknad
                        17.januar,
                    ),
            ),
        )
    val soeknadBehandlingsdagerGruppe2 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 8.februar til 24.februar,
                behandlingsdager =
                    setOf(
                        9.februar,
                        20.februar,
                    ),
            ),
            mockSoeknadBehandlingsdager().copy(
                // sortert på behandlingsdager istedenfor sykmeldingsperiode
                sykmeldingsperiode = 8.februar til 20.februar,
                behandlingsdager =
                    setOf(
                        13.februar,
                    ),
            ),
        )
    val soeknadBehandlingsdagerGruppe3 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                // gap mellom sykmeldingsperiode og forrige er innenfor forlengelse, men behandlingsdager er ikke det
                sykmeldingsperiode = 6.mars til 25.mars,
                behandlingsdager =
                    setOf(
                        14.mars,
                        20.mars,
                    ),
            ),
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 2.april til 27.april,
                behandlingsdager =
                    setOf(
                        5.april,
                        11.april,
                        18.april,
                        23.april,
                    ),
            ),
            mockSoeknadBehandlingsdager().copy(
                // delvis overlapp med forrige
                sykmeldingsperiode = 17.april til 8.mai,
                behandlingsdager =
                    setOf(
                        // samme dag som forrige søknad
                        23.april,
                        1.mai,
                        8.mai,
                    ),
            ),
        )
    val soeknadBehandlingsdagerGruppe4 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 6.juni til 26.juni,
                behandlingsdager =
                    setOf(
                        6.juni,
                        19.juni,
                        26.juni,
                    ),
            ),
            mockSoeknadBehandlingsdager().copy(
                // sortert på behandlingsdager istedenfor sykmeldingsperiode
                sykmeldingsperiode = 2.juni til 20.juni,
                behandlingsdager =
                    setOf(
                        11.juni,
                        // samme uke, men ulik dag som forrige søknad
                        20.juni,
                    ),
            ),
        )
    val soeknadBehandlingsdagerGruppe5 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 11.juli til 16.august,
                behandlingsdager =
                    setOf(
                        11.juli,
                        // gap på over én uke
                        12.august,
                        13.august,
                    ),
            ),
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 22.august til 28.august,
                behandlingsdager =
                    setOf(
                        23.august,
                        28.august,
                    ),
            ),
        )
    val soeknadBehandlingsdagerGruppe6 =
        listOf(
            mockSoeknadBehandlingsdager().copy(
                sykmeldingsperiode = 3.september til 19.september,
                behandlingsdager =
                    setOf(
                        18.september,
                    ),
            ),
        )

    val soeknader =
        listOf(
            soeknadUtenForespoersel1.soeknad,
            *soeknadBehandlingsdagerGruppe1.toTypedArray(),
            soeknadUtenForespoersel2.soeknad,
            *soeknadBehandlingsdagerGruppe2.toTypedArray(),
            soeknadUtenForespoersel3.soeknad,
            *soeknadBehandlingsdagerGruppe3.toTypedArray(),
            soeknadUtenForespoersel4.soeknad,
            soeknadUtenForespoersel5.soeknad,
            soeknadMedForespoersel1,
            soeknadMedForespoersel2,
            soeknadUtenForespoersel6.soeknad,
            soeknadUtenForespoersel7.soeknad,
            *soeknadBehandlingsdagerGruppe4.toTypedArray(),
            soeknadUtenForespoersel8.soeknad,
            *soeknadBehandlingsdagerGruppe5.toTypedArray(),
            soeknadMedForespoersel3,
            *soeknadBehandlingsdagerGruppe6.toTypedArray(),
            soeknadUtenForespoersel9.soeknad,
        )

    val forespoersler =
        listOf(
            forespoerselMedId1,
            forespoerselMedId2,
            forespoerselMedId3,
        )

    val soeknaderUtenForespoersel =
        listOf(
            soeknadUtenForespoersel1,
            soeknadUtenForespoersel2,
            soeknadUtenForespoersel3,
            soeknadUtenForespoersel4,
            soeknadUtenForespoersel5,
            soeknadUtenForespoersel6,
            soeknadUtenForespoersel7,
            soeknadUtenForespoersel8,
            soeknadUtenForespoersel9,
        )

    val soeknaderBehandlingsdager =
        listOf(
            soeknadBehandlingsdagerGruppe1,
            soeknadBehandlingsdagerGruppe2,
            soeknadBehandlingsdagerGruppe3,
            soeknadBehandlingsdagerGruppe4,
            soeknadBehandlingsdagerGruppe5,
            soeknadBehandlingsdagerGruppe6,
        )
}

private fun mockForespoerselMedId(soeknad: Soeknad.Arbeidstaker): ForespoerselMedId =
    ForespoerselMedId(
        forespoerselId = UUID.randomUUID(),
        forespoersel =
            mockForespoersel().copy(
                vedtaksperiodeId = soeknad.vedtaksperiodeId,
                sykmeldingsperioder = listOf(soeknad.sykmeldingsperiode),
                egenmeldingsperioder = soeknad.egenmeldingerFraSykmelding,
            ),
    )
