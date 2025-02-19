package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding

// midlertidlig duplisering for å støtte flere endringsÅrsaker
fun SkjemaInntektsmelding.fyllUtMangledeEndringsAarsaker(): SkjemaInntektsmelding {
    val medEndringsAarsakerfyllt = this.copy(inntekt = this.inntekt?.copy(endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak)))
    val verdiMangler = this.inntekt?.endringAarsaker.isNullOrEmpty()
    return if (verdiMangler) medEndringsAarsakerfyllt else this
}

fun Inntektsmelding.fyllUtMangledeEndringsAarsaker(): Inntektsmelding {
    val medEndringsAarsakerfyllt = this.copy(inntekt = this.inntekt?.copy(endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak)))
    val verdiMangler = this.inntekt?.endringAarsaker.isNullOrEmpty()
    return if (verdiMangler) medEndringsAarsakerfyllt else this
}

// fun LagretInntektsmelding.fyllUtMangledeEndringsAarsaker(): LagretInntektsmelding {
//
//
//    val medEndringsAarsakerfyllt = this.copy(inntekt = this.inntekt?.copy(endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak)))
//    val verdiMangler = this.inntekt?.endringAarsaker.isNullOrEmpty()
//    return if (verdiMangler) medEndringsAarsakerfyllt else this
// }
