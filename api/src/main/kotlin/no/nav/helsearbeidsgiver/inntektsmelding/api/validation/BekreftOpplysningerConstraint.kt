package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

class BekreftOpplysningerConstraint : CustomConstraint
fun <E> Validator<E>.Property<Boolean?>.isBekreftetOpplysninger() =
    this.validate(BekreftOpplysningerConstraint()) { it == true }
