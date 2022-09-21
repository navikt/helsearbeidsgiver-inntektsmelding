package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotEmpty
import org.valiktor.validate

@Serializable
data class InntektsmeldingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(InntektsmeldingRequest::orgnrUnderenhet).isNotEmpty()
            validate(InntektsmeldingRequest::identitetsnummer).isNotEmpty()
        }
    }
}
