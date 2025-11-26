@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.domene.inntektsmelding.v1

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
@Deprecated("Skal erstattes med im-domene 0.5.0")
data class InntektUtenNaturalytelser(
    val beloep: Double,
    val inntektsdato: LocalDate,
    val endringAarsaker: List<InntektEndringAarsak>,
)
