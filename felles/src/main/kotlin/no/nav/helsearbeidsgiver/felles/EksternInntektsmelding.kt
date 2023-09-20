@file:UseSerializers(LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class EksternInntektsmelding(
    val avsenderSystemNavn: String,
    val avsenderSystemVersjon: String,
    val arkivreferanse: String,
    val tidspunkt: LocalDateTime
)
data class InnsendtInntektsmelding(
    val dokument: InntektsmeldingDokument?,
    val eksternInntektsmelding: EksternInntektsmelding?
)
