package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
enum class BehovType {
    PAUSE,
    FULLT_NAVN,
    VIRKSOMHET,
    INNTEKT,
    ARBEIDSFORHOLD,
    JOURNALFOER,
    ARBEIDSGIVERE,
    HENT_TRENGER_IM,
    PREUTFYLL,
    PERSISTER_IM,
    HENT_PERSISTERT_IM,
    LAGRE_JOURNALPOST_ID,
    DISTRIBUER_IM,
    NOTIFIKASJON_TRENGER_IM,
    NOTIFIKASJON_IM_MOTTATT
}

@Serializable
enum class EventName {
    //@TODO trenger bedre navn.
    INSENDING_STARTED,
    INNTEKTSMELDING_MOTTATT,
    INNTEKTSMELDING_JOURNALFOERT,
    INNTEKTSMELDING_DISTRIBUERT,
    FORESPØRSEL_MOTTATT
}
