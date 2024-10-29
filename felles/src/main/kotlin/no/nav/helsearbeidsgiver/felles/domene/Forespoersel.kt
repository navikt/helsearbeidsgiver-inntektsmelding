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
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<String, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
) {
    fun forslagBestemmendeFravaersdag(): LocalDate {
        val forslag = bestemmendeFravaersdager[orgnr]
        return brukForslagEllerUtled(forslag)
    }

    fun forslagInntektsdato(): LocalDate {
        val forslag = bestemmendeFravaersdager.minOfOrNull { it.value }
        return brukForslagEllerUtled(forslag)
    }

    fun eksternBestemmendeFravaersdag(): LocalDate? =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull {
            it.value
        }

    private fun brukForslagEllerUtled(forslag: LocalDate?): LocalDate {
        val utledet =
            bestemmendeFravaersdag(
                arbeidsgiverperioder = emptyList(),
                sykefravaersperioder = egenmeldingsperioder.plus(sykmeldingsperioder).sortedBy { it.fom },
            )

        return if (forslag == null) {
            utledet
        } else {
            // Spleis hensyntar ikke sykmeldtes rapporterte egenmeldinger når de utleder forslaget sitt
            if (egenmeldingsperioder.isEmpty()) {
                forslag
            } else {
                minOf(forslag, utledet)
            }
        }
    }
}
