@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class TrengerInntekt(
    val type: ForespoerselType,
    val status: ForespoerselStatus,
    val orgnr: String,
    val fnr: String,
    val skjaeringstidspunkt: LocalDate?,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val forespurtData: ForespurtData
)

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}

enum class ForespoerselStatus {
    AKTIV,
    BESVART,
    FORKASTET
}
