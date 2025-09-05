@file:UseSerializers(LocalDateTimeSerializer::class)

package no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import java.time.LocalDateTime

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
