package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse

fun mapInntekt(response: InntektskomponentResponse): Inntekt {
    val list = mutableListOf<MottattHistoriskInntekt>()
    response.arbeidsInntektMaaned?.forEach { maaned ->
        maaned.arbeidsInntektInformasjon?.inntektListe?.map { inntekt ->
            list.add(MottattHistoriskInntekt(maaned.aarMaaned, inntekt.beloep ?: 0.0))
        }
    }
    val total: Double = list.sumOf { it.inntekt ?: 0.0 }
    return Inntekt(total, list)
}
