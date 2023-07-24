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
    TILGANGSKONTROLL,
    HENT_TRENGER_IM, // TODO: SPLEIS_FORESPOERSEL eller SPLEIS_FORESPOERSEL_DETALJER??
    PERSISTER_IM,
    HENT_PERSISTERT_IM,
    LAGRE_JOURNALPOST_ID,
    LAGRE_FORESPOERSEL,
    DISTRIBUER_IM,
    TRENGER_FORESPØRSEL,
    NOTIFIKASJON_IM_MOTTATT,
    NOTIFIKASJON_HENT_ID,
    OPPRETT_SAK,
    PERSISTER_SAK_ID,
    OPPRETT_OPPGAVE,
    PERSISTER_OPPGAVE_ID,
    PERSISTER_SENDT_EPOST_ID,
    HENT_IM_ORGNR
}

@Serializable
enum class EventName {
    // @TODO trenger bedre navn.
    HENT_PREUTFYLT,
    KVITTERING_REQUESTED,
    TRENGER_REQUESTED,
    INNTEKT_REQUESTED,
    INSENDING_STARTED,

    INNTEKTSMELDING_REQUESTED,
    INNTEKTSMELDING_MOTTATT,
    INNTEKTSMELDING_JOURNALFOERT,
    INNTEKTSMELDING_DISTRIBUERT,

    FORESPØRSEL_MOTTATT,
    FORESPOERSEL_BESVART,
    FORESPØRSEL_LAGRET,

    SAK_OPPRETTET,
    SAK_FERDIGSTILT,

    OPPGAVE_OPPRETTET,
    OPPGAVE_LAGRET,
    OPPGAVE_FERDIGSTILT
}
