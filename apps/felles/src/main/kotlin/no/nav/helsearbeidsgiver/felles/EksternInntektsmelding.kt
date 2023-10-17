@file:UseSerializers(LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class EksternInntektsmelding(
    val avsenderSystemNavn: String,
    val avsenderSystemVersjon: String,
    val arkivreferanse: String,
    val tidspunkt: LocalDateTime
)

@Serializable
data class InnsendtInntektsmelding(
    val dokument: Inntektsmelding? = null,
    val eksternInntektsmelding: EksternInntektsmelding? = null
)
