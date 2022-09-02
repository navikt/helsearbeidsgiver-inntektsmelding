package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.serialization.Serializable
import org.valiktor.functions.isNotEmpty
import org.valiktor.validate
import java.time.LocalDate

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

data class MottattPeriode(
    val fra: LocalDate,
    val til: LocalDate
)

data class MottattNaturalytelse(
    val type: String,
    val bortfallsdato: String,
    val verdi: Number
)

data class MottattHistoriskInntekt(
    val maanedsnavn: String,
    val inntekt: Number
)

data class MottattArbeidsforhold(
    val arbeidsforholdId: String,
    val arbeidsforhold: String,
    val stillingsprosent: Number
)

data class InntektsmeldingResponse(
    val navn: String,
    val identitetsnummer: String,
    val virksomhetsnavn: String,
    val orgnrUnderenhet: String,
    val fravaersperiode: List<MottattPeriode>,
    val egenmeldingsperioder: List<MottattPeriode>,
    val bruttoinntekt: Number,
    val tidligereinntekt: List<MottattHistoriskInntekt>,
    val behandlingsdager: List<String>,
    val behandlingsperiode: MottattPeriode,
    val arbeidsforhold: List<MottattArbeidsforhold>
)
