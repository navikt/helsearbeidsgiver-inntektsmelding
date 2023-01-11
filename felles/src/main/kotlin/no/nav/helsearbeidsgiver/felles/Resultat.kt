package no.nav.helsearbeidsgiver.felles

data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning? = null,
    val INNTEKT: InntektLøsning? = null,
    val JOURNALFOER: JournalpostLøsning? = null,
    val NOTIFIKASJON: NotifikasjonLøsning? = null,
    val HENT_TRENGER_IM: HentTrengerImLøsning? = null
)
