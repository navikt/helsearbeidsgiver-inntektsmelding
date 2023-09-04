package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
enum class BehovType {
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
    NOTIFIKASJON_HENT_ID,
    OPPRETT_SAK,
    PERSISTER_SAK_ID,
    OPPRETT_OPPGAVE,
    PERSISTER_OPPGAVE_ID,
    HENT_IM_ORGNR,
    HENT_AVSENDER_SYSTEM,
    LAGRE_AVSENDER_SYSTEM
}

@Serializable
enum class EventName {
    TILGANG_REQUESTED,
    KVITTERING_REQUESTED,
    TRENGER_REQUESTED,
    INNTEKT_REQUESTED,
    INSENDING_STARTED,

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
    OPPGAVE_FERDIGSTILT,

    AVSENDER_SYSTEM_LAGRET
}
