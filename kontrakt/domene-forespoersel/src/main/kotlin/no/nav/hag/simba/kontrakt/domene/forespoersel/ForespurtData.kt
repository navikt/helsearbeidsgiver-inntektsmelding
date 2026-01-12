package no.nav.hag.simba.kontrakt.domene.forespoersel

import kotlinx.serialization.Serializable

@Serializable
data class ForespurtData(
    val arbeidsgiverperiode: Arbeidsgiverperiode,
    val inntekt: Inntekt,
    val refusjon: Refusjon,
) {
    @Serializable
    data class Arbeidsgiverperiode(
        val paakrevd: Boolean,
    )

    @Serializable
    data class Inntekt(
        val paakrevd: Boolean,
    )

    @Serializable
    data class Refusjon(
        val paakrevd: Boolean,
    )
}
