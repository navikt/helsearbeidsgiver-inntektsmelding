package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.utils.sumMoney
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse

fun mapInntekt(response: InntektskomponentResponse, orgnr: String): Inntekt =
    if (orgnr.isEmpty()) {
        Inntekt(emptyList())
    } else {
        response.arbeidsInntektMaaned
            ?.associate {
                it.aarMaaned to it.arbeidsInntektInformasjon?.inntektListe
            }
            ?.filterKeyAndValueNotNull()
            ?.mapValues { (_, inntekter) ->
                inntekter
                    .filter { it.virksomhet?.identifikator == orgnr }
                    .mapNotNull { it.beloep }
                    .sumMoney()
            }
            ?.map { (maaned, inntekt) ->
                MottattHistoriskInntekt(maaned, inntekt)
            }
            .orEmpty()
            .let(::Inntekt)
    }

// FIXME: Se på om felter i InntektskomponentResponse trenger å være null, da kan dette kuttes ut
private fun <K : Any, V : Any> Map<K?, V?>.filterKeyAndValueNotNull(): Map<K, V> =
    mapNotNull { (key, value) ->
        if (key == null) {
            null
        } else if (value == null) {
            null
        } else {
            key to value
        }
    }
        .toMap()
