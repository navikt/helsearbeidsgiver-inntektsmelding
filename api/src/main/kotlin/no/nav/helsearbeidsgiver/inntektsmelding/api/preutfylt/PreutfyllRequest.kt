package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidOrganisasjonsnummer
import org.valiktor.validate

@Serializable
data class PreutfyllRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(PreutfyllRequest::orgnrUnderenhet).isValidOrganisasjonsnummer()
            validate(PreutfyllRequest::identitetsnummer).isValidIdentitetsnummer()
        }
    }
}
