package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import java.math.BigDecimal

fun mapInntekt(response: InntektskomponentResponse, orgnr: String): Inntekt {
    if (orgnr.isNullOrEmpty()) {
        return Inntekt(0.0, emptyList())
    }
    val list = response.arbeidsInntektMaaned?.map { it.aarMaaned to it.arbeidsInntektInformasjon?.inntektListe }
        ?.toMap()
        ?.filterKeyAndValueNotNull()
        ?.flatMap { (maaned, inntekter) ->
            inntekter.filter {
                it.virksomhet?.identifikator.equals(orgnr)
            }
                .map {
                    val belop = it.beloep ?: 0.0
                    MottattHistoriskInntekt(maaned, belop)
                }
        }
        .orEmpty()
    val sum = addAsBigDecimal(list)
    return Inntekt(sum.toDouble(), list)
}

private fun addAsBigDecimal(list: List<MottattHistoriskInntekt>): BigDecimal =
    list.map { BigDecimal.valueOf(it.inntekt ?: 0.0) }.fold(BigDecimal.ZERO) { a, b -> a + b }

// FIXME: Se på om felter i InntektskomponentResponse trenger å være null, da kan dette kuttes ut
private fun <K : Any, V : Any> Map<K?, V?>.filterKeyAndValueNotNull(): Map<K, V> =
    toList()
        .mapNotNull { (key, value) ->
            if (key == null) {
                null
            } else if (value == null) {
                null
            } else {
                key to value
            }
        }
        .toMap()
