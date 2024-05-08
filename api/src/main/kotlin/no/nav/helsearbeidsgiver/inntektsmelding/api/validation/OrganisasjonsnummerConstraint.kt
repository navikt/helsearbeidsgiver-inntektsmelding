package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.valiktor.Validator

object OrganisasjonsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isOrganisasjonsnummer() =
    this.validate(OrganisasjonsnummerConstraint) {
        require(it != null)
        Orgnr.erGyldig(it)
    }
