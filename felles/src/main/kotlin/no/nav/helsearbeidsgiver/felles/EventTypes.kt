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
    JOURNALFØRT_OK,
    LAGRE_JOURNALPOST_ID
}

@Serializable
enum class NotisType {
    NOTIFIKASJON,
    NOTIFIKASJON_TRENGER_IM
}
