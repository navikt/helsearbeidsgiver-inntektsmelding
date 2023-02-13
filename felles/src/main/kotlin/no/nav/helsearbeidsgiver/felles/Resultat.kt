package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning? = null,
    val INNTEKT: InntektLøsning? = null,
    val JOURNALFOER: JournalpostLøsning? = null,
    val NOTIFIKASJON: NotifikasjonLøsning? = null,
    val HENT_TRENGER_IM: HentTrengerImLøsning? = null,
    val PREUTFYLT: PreutfyltLøsning? = null,
    val PERSISTER_IM: PersisterImLøsning? = null
)
