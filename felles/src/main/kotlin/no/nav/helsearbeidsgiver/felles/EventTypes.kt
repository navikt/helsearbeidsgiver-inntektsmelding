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
    PREUTFYLL,
    PERSISTER_IM,
    HENT_PERSISTERT_IM,
    LAGRE_JOURNALPOST_ID,
    LAGRE_FORESPOERSEL,
    DISTRIBUER_IM,
    TRENGER_FORESPØRSEL,

    // NOTIFIKASJON_TRENGER_IM,
    NOTIFIKASJON_IM_MOTTATT,
    OPPRETT_SAK,
    PERSISTER_SAK_ID,
    OPPRETT_OPPGAVE,
    PERSISTER_OPPGAVE_ID,
    PERSISTER_SENDT_EPOST_ID,
    ENDRE_SAK_STATUS,
    ENDRE_OPPGAVE_STATUS,
    HENT_IM_ORGNR
}

@Serializable
enum class EventName {
    // @TODO trenger bedre navn.
    HENT_PREUTFYLT,
    KVITTERING_REQUESTED,
    INSENDING_STARTED,
    INNTEKTSMELDING_MOTTATT,
    INNTEKTSMELDING_JOURNALFOERT,
    INNTEKTSMELDING_DISTRIBUERT,
    FORESPØRSEL_MOTTATT,
    FORESPØRSEL_LAGRET,
    NOTIFIKASJON_FORESPØRT,
    SAK_OPPRETTET,
    INNTEKTSMELDING_REQUESTED,
    OPPGAVE_OPPRETTET,
    OPPGAVE_LAGRET,
    TRENGER_REQUESTED,
    TRENGER_REQUESTED2,
    INNTEKT_REQUESTED
}
