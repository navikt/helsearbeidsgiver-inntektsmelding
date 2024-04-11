@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode as PeriodeV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag as finnBestemmendeFravaersdag

// TODO nytt navn
@Serializable
data class TrengerInntekt(
    val type: ForespoerselType,
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    // TODO bytt til domene.v1.Periode
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<String, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean
) {
    fun bestemmendeFravaersdag(): LocalDate =
        bestemmendeFravaersdager[orgnr]
            ?: finnBestemmendeFravaersdag(
                arbeidsgiverperioder = emptyList(),
                egenmeldingsperioder = egenmeldingsperioder.map { PeriodeV1(it.fom, it.tom) },
                sykmeldingsperioder = sykmeldingsperioder.map { PeriodeV1(it.fom, it.tom) }
            )

    fun inntektsdato(): LocalDate? =
        bestemmendeFravaersdager.minOfOrNull { it.value }

    fun eksternBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull { it.value }
}

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}
