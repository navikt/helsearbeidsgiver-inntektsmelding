@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
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
    fun forslagBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager[orgnr]

    fun forslagInntektsdato(): LocalDate? =
        bestemmendeFravaersdager.minOfOrNull { it.value }

    fun eksternBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull { it.value }
}

enum class ForespoerselType {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}
