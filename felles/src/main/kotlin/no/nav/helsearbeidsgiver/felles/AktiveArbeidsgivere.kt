package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class AktiveArbeidsgivere(
    val fulltNavn: String? = null,
    val underenheter: List<Arbeidsgiver>
) {
    @Serializable
    data class Arbeidsgiver(
        val orgnrUnderenhet: String,
        val virksomhetsnavn: String
    )
}
