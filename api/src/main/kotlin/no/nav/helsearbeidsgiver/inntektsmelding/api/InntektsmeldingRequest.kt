package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotEmpty
import org.valiktor.validate

@Serializable
data class InntektsmeldingRequest(
    val organisasjonsnummer: String,
    val fødselsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(InntektsmeldingRequest::organisasjonsnummer).isNotEmpty()
            validate(InntektsmeldingRequest::fødselsnummer).isNotEmpty()
        }
    }
}
