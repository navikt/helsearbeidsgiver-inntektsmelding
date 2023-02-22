package no.nav.helsearbeidsgiver.felles

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
    SEND_IM_TIL_SPLEIS
}

enum class NotisType {
    NOTIFIKASJON,
    NOTIFIKASJON_TRENGER_IM
}

enum class EventName {
    INNTEKTSMELDING_MOTTATT
}
