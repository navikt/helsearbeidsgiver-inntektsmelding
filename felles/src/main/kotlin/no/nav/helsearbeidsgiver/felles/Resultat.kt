package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val TILGANGSKONTROLL: TilgangskontrollLøsning? = null
)

@Serializable
data class TrengerData(
    val fnr: String? = null,
    val orgnr: String? = null,
    val personDato: PersonDato? = null,
    val virksomhetNavn: String? = null,
    val arbeidsforhold: List<Arbeidsforhold>? = null,
    val inntekt: Inntekt? = null,
    val fravarsPerioder: List<Periode>? = null,
    val egenmeldingsPerioder: List<Periode>? = null,
    val forespurtData: ForespurtData? = null,
    val bruttoinntekt: Double? = null,
    val tidligereinntekter: List<InntektPerMaaned>? = null,
    val feilReport: FeilReport? = null
)

@Serializable
data class InntektData(
    val inntekt: Inntekt? = null,
    val feil: FeilReport? = null
)
