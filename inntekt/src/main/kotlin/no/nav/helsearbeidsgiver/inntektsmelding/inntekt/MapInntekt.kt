package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse

fun mapInntekt(response: InntektskomponentResponse, orgnr: String): Inntekt {
    if (orgnr.isEmpty()) {
        return Inntekt(emptyList())
    }
    val list = response.arbeidsInntektMaaned?.map { it.aarMaaned to it.arbeidsInntektInformasjon?.inntektListe }
        ?.toMap()
        ?.filterKeyAndValueNotNull()
        ?.flatMap { (maaned, inntekter) ->
            inntekter.filter {
                it.virksomhet?.identifikator == orgnr
            }
                .map {
                    val belop = it.beloep ?: 0.0
                    MottattHistoriskInntekt(maaned, belop)
                }
        }
        .orEmpty()
    return Inntekt(list)
}

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
