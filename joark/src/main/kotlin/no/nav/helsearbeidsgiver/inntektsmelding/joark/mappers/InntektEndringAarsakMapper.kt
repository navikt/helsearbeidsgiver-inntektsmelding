package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.VarigLonnsendring
import org.mapstruct.Mapper

fun InntektEndringAarsak.stringValue(): String {
    return when (this) {
        is Nyansatt -> "Nyansatt"
        is Bonus -> "Bonus" // Beløp og dato ikke implementert i frontend
        is Ferie -> "Ferie: ${liste.lesbar()}"
        is Ferietrekk -> "Ferietrekk"
        is Sykefravaer -> "Sykefravær: ${liste.lesbar()}"
        is NyStilling -> "Ny stilling: fra $gjelderFra"
        is NyStillingsprosent -> "Ny stillingsprosent: fra $gjelderFra"
        is Permisjon -> "Permisjon: ${liste.lesbar()}"
        is Permittering -> "Permittering: ${liste.lesbar()}"
        is Tariffendring -> "Tariffendring: fra $gjelderFra"
        is VarigLonnsendring -> "Varig lønnsendring: fra $gjelderFra"
        is Feilregistrert -> "Mangelfull eller uriktig rapportering til A-ordningen"
    }
}

private fun List<Periode>.lesbar(): String =
    joinToString { "fra ${it.fom} til ${it.tom}" }

@Mapper
abstract class InntektEndringAarsakMapper {
    fun inntektEndringAarsakTilString(inntektEndringAarsak: InntektEndringAarsak?): String? = inntektEndringAarsak?.stringValue()
}
