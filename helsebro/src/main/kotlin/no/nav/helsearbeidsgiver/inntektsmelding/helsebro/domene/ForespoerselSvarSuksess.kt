@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: ForespoerselSvarSuksess? = null,
    val feil: ForespoerselSvarFeil? = null,
    val boomerang: Map<String, JsonElement>
)

@Serializable
data class ForespoerselSvarSuksess(
    val orgnr: String,
    val fnr: String,
    val sykmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtData>
)

@Serializable
data class ForespoerselSvarFeil(
    val feilkode: Feilkode,
    val feilmelding: String
) {
    enum class Feilkode {
        FORESPOERSEL_IKKE_FUNNET
    }
}
