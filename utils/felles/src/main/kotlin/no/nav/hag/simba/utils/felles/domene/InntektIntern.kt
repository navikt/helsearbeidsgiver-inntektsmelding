@file:UseSerializers(LocalDateSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class InntektIntern(
    val beloep: Double,
    val inntektsdato: LocalDate,
    val naturalytelser: List<Naturalytelse> = emptyList(),
    val endringAarsaker: List<InntektEndringAarsak>,
) {
    fun tilGammeltFormat(): Inntekt =
        Inntekt(
            beloep = beloep,
            inntektsdato = inntektsdato,
            naturalytelser = naturalytelser,
            endringAarsaker = endringAarsaker,
        )
}
