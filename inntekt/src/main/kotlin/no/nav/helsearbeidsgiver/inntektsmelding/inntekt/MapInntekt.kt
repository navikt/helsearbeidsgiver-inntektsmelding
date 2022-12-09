package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import java.math.BigDecimal

fun mapInntekt(response: InntektskomponentResponse, orgnr: String): Inntekt {
    val list = mutableListOf<MottattHistoriskInntekt>()
    var sum = BigDecimal(0.0)
    if (orgnr.isNullOrEmpty()) {
        return Inntekt(sum.toDouble(), list)
    }
    response.arbeidsInntektMaaned?.forEach { maaned ->
        maaned.arbeidsInntektInformasjon?.inntektListe?.filter {
            it.virksomhet?.identifikator.equals(orgnr)
        }?.toList()?.map { inntekt ->
            val belop = inntekt.beloep ?: 0.0
            list.add(MottattHistoriskInntekt(maaned.aarMaaned, belop))
            sum = sum.add(BigDecimal.valueOf(belop))
        }
    }
    val total = sum.toDouble()
    return Inntekt(total, list)
}
