package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Feilregistrert
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferietrekk
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektEndringAarsak
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Nyansatt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Sykefravaer
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import org.mapstruct.Mapper

fun InntektEndringAarsak.stringValue(): String {
    return when (this) {
        is Nyansatt -> "Nyansatt"
        is Bonus -> "Bonus" // Beløp og dato ikke implementert i frontend
        is Ferie -> "$typpe: ${liste.lesbar()}"
        is Sykefravaer -> "Sykefravær: ${liste.lesbar()}"
        is NyStilling -> "Ny stilling: fra $gjelderFra"
        is NyStillingsprosent -> "Ny stillingsprosent: fra $gjelderFra"
        is Permisjon -> "$typpe: ${liste.lesbar()}"
        is Permittering -> "$typpe: ${liste.lesbar()}"
        is Tariffendring -> "$typpe: fra $gjelderFra"
        is VarigLonnsendring -> "Varig lønnsendring: fra $gjelderFra"
        is Feilregistrert -> "Mangelfull eller uriktig rapportering til A-ordningen"
        is Ferietrekk -> "Ferietrekk"
        else -> typpe
    }
}

private fun List<Periode>.lesbar(): String =
    joinToString { "fra ${it.fom} til ${it.tom}" }

@Mapper
abstract class InntektEndringAarsakMapper {
    fun inntektEndringAarsakTilString(inntektEndringAarsak: InntektEndringAarsak?): String? = inntektEndringAarsak?.stringValue()
}
