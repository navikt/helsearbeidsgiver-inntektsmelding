package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.VarigLonnsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
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
