package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeIn
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.inntektsmelding.xml.kodeliste._20210216.BegrunnelseIngenEllerRedusertUtbetalingKodeliste
import no.nav.inntektsmelding.xml.kodeliste._20210216.NaturalytelseKodeliste

class EnumTilKodeverkListeTest : FunSpec({
    context("Sjekk at NaturalytelseKodeliste i kodeverk tilsvarer NaturalYtelseKode i domenemodellen") {
        withData(NaturalytelseKode.entries) { kode ->
            kode.value shouldBeIn NaturalytelseKodeliste.entries.map { it.value().uppercase() }
        }
    }

    context("Sjekk at BegrunnelseIngenEllerRedusertUtbetalingKodeliste i kodeverk tilsvarer BegrunnelseIngenEllerRedusertUtbetalingKode i domenemodellen") {
        withData(BegrunnelseIngenEllerRedusertUtbetalingKode.entries.map { it.value }) { kode ->
            kode shouldBeIn BegrunnelseIngenEllerRedusertUtbetalingKodeliste.entries.map { it.value() }
        }
    }
})
