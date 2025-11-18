@file:UseSerializers(LocalDateTimeSerializer::class)

package no.nav.hag.simba.kontrakt.domene.inntektsmelding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import java.time.LocalDateTime
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

@Serializable
sealed class LagretInntektsmelding {
    @Serializable
    @SerialName("Skjema")
    data class Skjema(
        val avsenderNavn: String?,
        val skjema: SkjemaInntektsmelding,
        val mottatt: LocalDateTime,
    ) : LagretInntektsmelding()

    @Serializable
    @SerialName("Ekstern")
    data class Ekstern(
        val ekstern: EksternInntektsmelding,
    ) : LagretInntektsmelding()
}
