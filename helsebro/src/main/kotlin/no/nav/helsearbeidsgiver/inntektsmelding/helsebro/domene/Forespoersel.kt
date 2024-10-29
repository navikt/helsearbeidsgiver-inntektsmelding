@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

@Serializable
data class Forespoersel(
    val orgnr: Orgnr,
    val fnr: Fnr,
    /** Ikke bruk ved henting av én forespørsel (Storebror lekker feil id). */
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
            orgnr = orgnr.verdi,
            fnr = fnr.verdi,
            vedtaksperiodeId = vedtaksperiodeId,
            sykmeldingsperioder = sykmeldingsperioder,
            egenmeldingsperioder = egenmeldingsperioder,
            bestemmendeFravaersdager = bestemmendeFravaersdager.mapKeys { it.key.verdi },
            forespurtData = forespurtData,
            erBesvart = erBesvart,
        )
}
