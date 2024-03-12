@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class TrengerInntekt(
    val type: ForespoerselType,
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val skjaeringstidspunkt: LocalDate?,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean
)

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}
