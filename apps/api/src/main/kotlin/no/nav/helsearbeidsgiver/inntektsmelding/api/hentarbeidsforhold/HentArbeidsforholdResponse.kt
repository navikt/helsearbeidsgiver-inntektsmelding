@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class AnsettelsesforholdResponse(
    val startdato: LocalDate,
    val yrkesbeskrivelse: String?,
    val stillingsprosent: Double?,
) {
    companion object {
        fun fra(ansettelsesforhold: Ansettelsesforhold): AnsettelsesforholdResponse =
            AnsettelsesforholdResponse(
                startdato = ansettelsesforhold.startdato,
                yrkesbeskrivelse = ansettelsesforhold.yrkesbeskrivelse,
                stillingsprosent = ansettelsesforhold.stillingsprosent,
            )
    }
}

@Serializable
data class HentArbeidsforholdResponse(
    val ansettelsesforhold: Set<AnsettelsesforholdResponse>,
)
