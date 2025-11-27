@file:UseSerializers(UuidSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Deprecated("Skal erstattes med im-domene 0.5.0")
data class SkjemaInntektsmeldingIntern(
    val forespoerselId: UUID,
    val avsenderTlf: String,
    val agp: Arbeidsgiverperiode?,
    val inntekt: Inntekt?,
    val refusjon: Refusjon?,
    @EncodeDefault
    val naturalytelser: List<Naturalytelse> = inntekt?.naturalytelser.orEmpty(),
) {
    fun valider(): Set<String> {
        val naturalytelserFeilmelding =
            if (naturalytelser.all { it.verdiBeloep > 0 && it.verdiBeloep < 1_000_000 }) {
                null
            } else {
                "Beløp må være større enn 0"
            }

        return SkjemaInntektsmelding(forespoerselId, avsenderTlf, agp, inntekt, refusjon)
            .valider()
            .plus(naturalytelserFeilmelding)
            .mapNotNull { it }
            .toSet()
    }
}
