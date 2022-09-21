package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotEmpty
import org.valiktor.validate

@Serializable
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
