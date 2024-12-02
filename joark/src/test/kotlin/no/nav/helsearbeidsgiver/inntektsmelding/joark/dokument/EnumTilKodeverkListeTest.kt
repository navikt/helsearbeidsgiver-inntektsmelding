package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeIn
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.inntektsmelding.xml.kodeliste._20210216.BegrunnelseIngenEllerRedusertUtbetalingKodeliste
import no.nav.inntektsmelding.xml.kodeliste._20210216.NaturalytelseKodeliste

class EnumTilKodeverkListeTest :
    FunSpec({
        context("Sjekk at NaturalytelseKodeliste i kodeverk tilsvarer 'Naturalytelse.Kode' i domenemodellen") {
            withData(Naturalytelse.Kode.entries) { kode ->
                kode.name shouldBeIn NaturalytelseKodeliste.entries.map { it.value().uppercase() }
            }
        }

        context("Sjekk at BegrunnelseIngenEllerRedusertUtbetalingKodeliste i kodeverk tilsvarer 'RedusertLoennIAgp.Begrunnelse' i domenemodellen") {
            withData(RedusertLoennIAgp.Begrunnelse.entries) { kode ->
                kode.name shouldBeIn BegrunnelseIngenEllerRedusertUtbetalingKodeliste.entries.map { it.value() }
            }
        }
    })
