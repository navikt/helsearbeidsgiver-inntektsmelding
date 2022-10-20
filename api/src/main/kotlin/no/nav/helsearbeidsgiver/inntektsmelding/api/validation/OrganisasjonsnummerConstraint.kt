package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

class OrganisasjonsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isValidOrganisasjonsnummer() =
    this.validate(OrganisasjonsnummerConstraint()) { OrganisasjonsnummerValidator.isValid(it) }
