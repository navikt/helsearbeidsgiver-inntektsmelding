package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.inntektsmelding.xml.kodeliste._20210216.BegrunnelseIngenEllerRedusertUtbetalingKodeliste
import no.nav.inntektsmelding.xml.kodeliste._20210216.NaturalytelseKodeliste
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EnumTilKodeverkListeTest {
    @Test
    fun `Sjekk at NaturalytelseKodeliste i kodeverk tilsvarer NaturalYtelseKode i domenemodellen`() {
        Assertions.assertEquals(NaturalytelseKodeliste.entries.size, NaturalytelseKode.entries.size)
        val simbaNaturalytelseKodeListe = NaturalytelseKode.entries.map { it.value }
        NaturalytelseKodeliste.entries.forEach {
            Assertions.assertTrue(simbaNaturalytelseKodeListe.contains(it.value().uppercase()))
        }
    }

    @Test
    fun `sjekk at BegrunnelseIngenEllerRedusertUtbetalingKodeliste i kodeverk tilsvarer BegrunnelseIngenEllerRedusertUtbetalingKode i domenemodellen`() {
        Assertions.assertEquals(BegrunnelseIngenEllerRedusertUtbetalingKodeliste.entries.size, BegrunnelseIngenEllerRedusertUtbetalingKode.entries.size)
        BegrunnelseIngenEllerRedusertUtbetalingKodeliste.entries.forEach {
            Assertions.assertEquals(it.name, BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(it.name).name)
        }
    }
}
