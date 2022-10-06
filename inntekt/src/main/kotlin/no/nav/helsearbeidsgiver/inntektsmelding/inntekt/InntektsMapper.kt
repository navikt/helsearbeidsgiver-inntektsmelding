package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse

fun mapInntekt(response: InntektskomponentResponse): Inntekt {
    val list = mutableListOf<MottattHistoriskInntekt>()
    response.arbeidsInntektMaaned?.forEach { maaned ->
        maaned.arbeidsInntektInformasjon?.inntektListe?.forEach { inntekt ->
            MottattHistoriskInntekt(maaned.aarMaaned, inntekt.beloep)
        }
    }
    return Inntekt(0.0, list)
}
