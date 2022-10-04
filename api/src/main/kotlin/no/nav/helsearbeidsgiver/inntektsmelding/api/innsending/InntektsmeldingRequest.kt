package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidOrganisasjonsnummer
import org.valiktor.validate

@Serializable
data class InntektsmeldingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(InntektsmeldingRequest::orgnrUnderenhet).isValidOrganisasjonsnummer()
            validate(InntektsmeldingRequest::identitetsnummer).isValidIdentitetsnummer()
        }
    }
}
