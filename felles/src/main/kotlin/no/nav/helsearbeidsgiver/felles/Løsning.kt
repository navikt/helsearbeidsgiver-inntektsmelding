@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold

sealed class Løsning(
    // var value: Any? = null,
    open var error: Feilmelding? = null
)

data class Feilmelding(
    val melding: String,
    val status: Int? = null
)

data class NavnLøsning(
    var value: String? = null,
    override var error: Feilmelding? = null
) : Løsning(error)

data class VirksomhetLøsning(
    var value: String? = null,
    override var error: Feilmelding? = null
) : Løsning(error)

data class InntektLøsning(
    var value: Inntekt? = null,
    override var error: Feilmelding? = null
) : Løsning(error)

data class ArbeidsforholdLøsning(
    var value: List<Arbeidsforhold> = emptyList(),
    override var error: Feilmelding? = null
) : Løsning(error)

data class SykLøsning(
    var value: Syk? = null,
    override var error: Feilmelding? = null
) : Løsning(error)
