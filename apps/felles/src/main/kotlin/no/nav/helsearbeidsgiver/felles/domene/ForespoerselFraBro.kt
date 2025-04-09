@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselFraBro(
    val orgnr: Orgnr,
    val fnr: Fnr,
    val forespoerselId: UUID,
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
) {
    fun toForespoersel(): Forespoersel =
        Forespoersel(
            orgnr = orgnr,
            fnr = fnr,
            vedtaksperiodeId = vedtaksperiodeId,
            sykmeldingsperioder = sykmeldingsperioder,
            egenmeldingsperioder = egenmeldingsperioder,
            bestemmendeFravaersdager = bestemmendeFravaersdager,
            forespurtData = forespurtData,
            erBesvart = erBesvart,
        )
}
