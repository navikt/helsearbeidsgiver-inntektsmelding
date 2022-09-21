package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import org.valiktor.functions.isNotEmpty
import org.valiktor.validate

data class PreutfyllRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(PreutfyllRequest::orgnrUnderenhet).isNotEmpty()
            validate(PreutfyllRequest::identitetsnummer).isNotEmpty()
        }
    }
}
