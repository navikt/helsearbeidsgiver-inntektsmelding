@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.time.LocalDate
import java.util.UUID

// TODO nytt navn
@Serializable
data class TrengerInntekt(
    val type: ForespoerselType,
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<String, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean
) {
    fun bestemmendeFravaersdag(): LocalDate =
        bestemmendeFravaersdager[orgnr]
            ?: finnBestemmendeFravaersdag()

    fun inntektsdato(): LocalDate? =
        bestemmendeFravaersdager.minOfOrNull { it.value }

    fun eksternBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull { it.value }

    /** Perioder som ikke har opphold over hverdager anses som sammenhengende. */
    private fun finnBestemmendeFravaersdag(): LocalDate =
        (egenmeldingsperioder + sykmeldingsperioder)
            .sortedBy { it.fom }
            .reduce { p1, p2 ->
                p1.slåSammenIgnorerHelgOrNull(p2)
                    .orDefault(p2)
            }
            .fom
}

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}
