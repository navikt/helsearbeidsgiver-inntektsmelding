package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
enum class BehovType {
    // Hente data
    HENT_ARBEIDSFORHOLD,
    ARBEIDSGIVERE,
    FULLT_NAVN,
    HENT_EKSTERN_INNTEKTSMELDING,
    HENT_INNTEKT,
    HENT_LAGRET_IM,
    HENT_PERSONER,
    HENT_SELVBESTEMT_IM,
    HENT_TRENGER_IM, // TODO: SPLEIS_FORESPOERSEL eller SPLEIS_FORESPOERSEL_DETALJER??
    HENT_VIRKSOMHET_NAVN,
    NOTIFIKASJON_HENT_ID,
    TILGANGSKONTROLL,

    // Synkrone endringer
    LAGRE_SELVBESTEMT_IM,
    OPPRETT_SELVBESTEMT_SAK,
    PERSISTER_IM,
    SLETT_SAK, // kun brukt ved manuell kjøring

    // Asynkrone endringer
    LAGRE_EKSTERN_INNTEKTSMELDING, // kan erstattes av event
    LAGRE_FORESPOERSEL, // kan erstattes av event
    LAGRE_JOURNALPOST_ID, // kan erstattes av event
    OPPRETT_OPPGAVE, // blir overflødig ved flytting av notifikasjondatabase
    OPPRETT_SAK, // blir overflødig ved flytting av notifikasjondatabase
    PERSISTER_OPPGAVE_ID, // kan erstattes av event
    PERSISTER_SAK_ID, // kan erstattes av event

    // Asynkrone endringer, men brukt til å prøve igjen ved feil
    JOURNALFOER,
    DISTRIBUER_IM,
}

@Serializable
enum class EventName {
    TILGANG_FORESPOERSEL_REQUESTED,
    TILGANG_ORG_REQUESTED,
    TRENGER_REQUESTED,
    INNTEKT_REQUESTED,
    INNTEKT_SELVBESTEMT_REQUESTED,
    KVITTERING_REQUESTED,
    SELVBESTEMT_IM_REQUESTED,
    AKTIVE_ORGNR_REQUESTED,

    INSENDING_STARTED,

    SELVBESTEMT_IM_MOTTATT,
    SELVBESTEMT_IM_LAGRET,

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
    OPPGAVE_FERDIGSTILT,
}
