@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: Suksess? = null,
    val feil: Feil? = null,
    val boomerang: JsonElement,
) {
    @Serializable
    data class Suksess(
        val type: ForespoerselType,
        val orgnr: String,
        val fnr: String,
        val vedtaksperiodeId: UUID,
        val skjaeringstidspunkt: LocalDate?,
        val sykmeldingsperioder: List<Periode>,
        val egenmeldingsperioder: List<Periode>,
        val bestemmendeFravaersdager: Map<String, LocalDate>,
        val forespurtData: ForespurtData,
        val erBesvart: Boolean,
    )

    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET,
    }
}
