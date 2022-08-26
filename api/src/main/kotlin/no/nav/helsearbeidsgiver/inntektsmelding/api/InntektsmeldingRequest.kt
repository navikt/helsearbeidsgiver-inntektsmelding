package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotEmpty
import org.valiktor.validate

@Serializable
data class InntektsmeldingRequest(
    val fnr: String,
    val orgnr: String,
    val perioder: List<Periode> = emptyList()
) {
    fun validate() {
        validate(this) {
            validate(InntektsmeldingRequest::fnr).isNotEmpty()
            validate(InntektsmeldingRequest::orgnr).isNotEmpty()
        }
    }
}

@Serializable
data class Periode(
    val fom: String,
    val tom: String
)
