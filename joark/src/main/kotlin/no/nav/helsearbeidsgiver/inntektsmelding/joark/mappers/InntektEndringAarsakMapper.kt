package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektEndringAarsak
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import org.mapstruct.Mapper

fun InntektEndringAarsak.stringValue(): String {
    return when (this) {
        is Bonus -> "Bonus" // Beløp og dato ikke implementert i frontend
        is Ferie -> typpe + ": " + liste.joinToString(transform = printPeriode())
        is NyStilling -> "Ny stilling: fra $gjelderFra"
        is NyStillingsprosent -> "Ny stillingsprosent: fra $gjelderFra"
        is Permisjon -> typpe + ": " + liste.joinToString(transform = printPeriode())
        is Permittering -> typpe + ": " + liste.joinToString(transform = printPeriode())
        is Tariffendring -> typpe + ": fra $gjelderFra"
        is VarigLonnsendring -> "Varig lønnsendring: fra $gjelderFra"
        else -> "" + typpe
    }
}

private fun printPeriode(): (Periode) -> CharSequence = { "fra ${it.fom} til ${it.tom}" }

@Mapper
abstract class InntektEndringAarsakMapper {
    fun inntektEndringAarsakTilString(inntektEndringAarsak: InntektEndringAarsak?): String? = inntektEndringAarsak?.stringValue()
}
