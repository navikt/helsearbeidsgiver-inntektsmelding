package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotBlank

@Serializable
data class TrengerRequest(
    val uuid: String
) {
    fun validate() {
        org.valiktor.validate(this) {
            validate(TrengerRequest::uuid).isNotBlank()
        }
    }
}
