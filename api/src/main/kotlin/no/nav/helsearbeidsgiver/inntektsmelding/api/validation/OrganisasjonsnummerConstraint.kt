package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

object OrganisasjonsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isOrganisasjonsnummer() =
    this.validate(OrganisasjonsnummerConstraint) { OrganisasjonsnummerValidator.isValid(it) }
