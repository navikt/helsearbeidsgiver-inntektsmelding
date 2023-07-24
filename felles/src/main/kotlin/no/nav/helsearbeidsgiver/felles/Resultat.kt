package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning? = null,
    val INNTEKT: InntektLøsning? = null,
    val HENT_TRENGER_IM: HentTrengerImLøsning? = null,
    val PREUTFYLL: PreutfyltLøsning? = null,
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
    val forespurtData: List<ForespurtData>? = null,
    val bruttoinntekt: Double? = null,
    val tidligereinntekter: List<MottattHistoriskInntekt>? = null,
    val feilReport: FeilReport? = null

)
