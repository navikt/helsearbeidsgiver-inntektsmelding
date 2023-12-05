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
    SLETT_SAK,
    PERSISTER_SAK_ID,
    OPPRETT_OPPGAVE,
    PERSISTER_OPPGAVE_ID,
    HENT_IM_ORGNR,
    HENT_EKSTERN_INNTEKTSMELDING,
    LAGRE_EKSTERN_INNTEKTSMELDING
}

@Serializable
enum class EventName {
    TILGANG_REQUESTED,
    KVITTERING_REQUESTED,
    TRENGER_REQUESTED,
    INNTEKT_REQUESTED,
    INSENDING_STARTED,

    EKSTERN_INNTEKTSMELDING_REQUESTED,
    EKSTERN_INNTEKTSMELDING_MOTTATT,
    EKSTERN_INNTEKTSMELDING_LAGRET,

    INNTEKTSMELDING_MOTTATT,
    INNTEKTSMELDING_JOURNALFOERT,
    INNTEKTSMELDING_DISTRIBUERT,

    FORESPØRSEL_MOTTATT,
    FORESPOERSEL_BESVART,
    FORESPØRSEL_LAGRET,

    SAK_OPPRETT_REQUESTED,
    SAK_OPPRETTET,
    SAK_FERDIGSTILT,
    MANUELL_OPPRETT_SAK_REQUESTED,
    MANUELL_SLETT_SAK_REQUESTED,

    OPPGAVE_OPPRETT_REQUESTED,
    OPPGAVE_OPPRETTET,
    OPPGAVE_LAGRET,
    OPPGAVE_FERDIGSTILT
}
