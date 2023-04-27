package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

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
        is Ferie -> this.typpe + ": " + liste.joinToString(transform = printPeriode())
        is NyStilling -> "Ny stilling: fra ${this.gjelderFra}"
        is NyStillingsprosent -> "Ny stillingsprosent: fra ${this.gjelderFra}"
        is Permisjon -> this.typpe + ": " + liste.joinToString(transform = printPeriode())
        is Permittering -> this.typpe + ": " + liste.joinToString(transform = printPeriode())
        is Tariffendring -> this.typpe + ": fra ${this.gjelderFra}"
        is VarigLonnsendring -> "Varig lønnsendring: fra ${this.gjelderFra}"
        else -> "" + this.typpe
    }
}

private fun printPeriode(): (Periode) -> CharSequence = { "fra ${it.fom} til ${it.tom}" }

@Mapper
abstract class InntektEndringAarsakMapper {
    fun inntektEndringAarsakTilString(inntektEndringAarsak: InntektEndringAarsak): String = inntektEndringAarsak.stringValue()
}
