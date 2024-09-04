@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class Forespoersel(
    val type: ForespoerselType,
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<String, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
) {
    fun forslagBestemmendeFravaersdag(): LocalDate =
        finnTidligste(
            spleisForslag = bestemmendeFravaersdager[orgnr],
            beregnet =
                bestemmendeFravaersdag(
                    arbeidsgiverperioder = emptyList(),
                    sykefravaersperioder = sykmeldingsperioder + egenmeldingsperioder,
                ),
        )

    fun forslagInntektsdato(): LocalDate =
        finnTidligste(
            spleisForslag = bestemmendeFravaersdager.minOfOrNull { it.value },
            beregnet =
                bestemmendeFravaersdag(
                    arbeidsgiverperioder = emptyList(),
                    sykefravaersperioder = sykmeldingsperioder + egenmeldingsperioder,
                ),
        )

    fun eksternBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull {
            it.value
        }
}

private fun finnTidligste(
    spleisForslag: LocalDate?,
    beregnet: LocalDate,
): LocalDate = listOfNotNull(spleisForslag, beregnet).min()

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL,
}
