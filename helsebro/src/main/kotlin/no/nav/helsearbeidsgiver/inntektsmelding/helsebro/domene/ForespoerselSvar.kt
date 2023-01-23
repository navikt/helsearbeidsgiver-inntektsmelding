@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val orgnr: String,
    val fnr: String,
    val forespoerselId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtData>,
    val boomerang: Map<String, JsonElement>
)
