package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOrganisasjonsnummer
import org.valiktor.validate

@Serializable
data class PreutfyltRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String
) {
    fun validate() {
        validate(this) {
            validate(PreutfyltRequest::orgnrUnderenhet).isOrganisasjonsnummer()
            validate(PreutfyltRequest::identitetsnummer).isIdentitetsnummer()
        }
    }
}
