@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselType
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselListeSvar(
    val resultat: List<Forespoersel>,
    val boomerang: JsonElement,
) {
    @Serializable
    data class Forespoersel(
        val type: ForespoerselType,
        val orgnr: Orgnr,
        val fnr: String,
        val forespoerselId: UUID,
        val vedtaksperiodeId: UUID,
        val egenmeldingsperioder: List<Periode>,
        val sykmeldingsperioder: List<Periode>,
        val skjaeringstidspunkt: LocalDate?,
        val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
        val forespurtData: ForespurtData,
        val erBesvart: Boolean,
    )
}
