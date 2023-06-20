package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning? = null,
    val INNTEKT: InntektLøsning? = null,
    val NOTIFIKASJON: NotifikasjonLøsning? = null,
    val HENT_TRENGER_IM: HentTrengerImLøsning? = null,
    val PREUTFYLL: PreutfyltLøsning? = null,
    val PERSISTER_IM: PersisterImLøsning? = null,
    val TILGANGSKONTROLL: TilgangskontrollLøsning? = null
)
@Serializable
data class TrengerData(
    val personDato: PersonDato? = null,
    val virksomhetNavn: String? = null,
    val arbeidsforhold: List<Arbeidsforhold>? = null,
    val intekt: Inntekt? = null,
    val fravarsPerioder: List<Periode>?,
    val egenmeldingsPerioder: List<Periode>?,
    val forespurtData: List<ForespurtData>?,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<MottattHistoriskInntekt>?,

)
