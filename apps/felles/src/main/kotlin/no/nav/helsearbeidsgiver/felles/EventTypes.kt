package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
enum class BehovType {
    // Hente data
    HENT_ARBEIDSFORHOLD,
    ARBEIDSGIVERE,
    HENT_INNTEKT,
    HENT_LAGRET_IM,
    HENT_PERSONER,
    HENT_SELVBESTEMT_IM,
    HENT_TRENGER_IM, // TODO: SPLEIS_FORESPOERSEL eller SPLEIS_FORESPOERSEL_DETALJER??
    HENT_VIRKSOMHET_NAVN,
    TILGANGSKONTROLL,
    HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE,

    // Synkrone endringer
    LAGRE_IM_SKJEMA,
    LAGRE_IM,
    LAGRE_SELVBESTEMT_IM,
    OPPRETT_SELVBESTEMT_SAK,
}

@Serializable
enum class EventName {
    TILGANG_FORESPOERSEL_REQUESTED,
    TILGANG_ORG_REQUESTED,
    TRENGER_REQUESTED,
    FORESPOERSLER_REQUESTED,
    INNTEKT_REQUESTED,
    INNTEKT_SELVBESTEMT_REQUESTED,
    KVITTERING_REQUESTED,
    SELVBESTEMT_IM_REQUESTED,
    AKTIVE_ORGNR_REQUESTED,

    API_INNSENDING_STARTET,
    INSENDING_STARTED,

    SELVBESTEMT_IM_MOTTATT,
    SELVBESTEMT_IM_LAGRET,

    EKSTERN_INNTEKTSMELDING_MOTTATT,
    EKSTERN_INNTEKTSMELDING_LAGRET,

    INNTEKTSMELDING_SKJEMA_LAGRET,
    INNTEKTSMELDING_MOTTATT,
    INNTEKTSMELDING_JOURNALFOERT,
    INNTEKTSMELDING_JOURNALPOST_ID_LAGRET,
    INNTEKTSMELDING_DISTRIBUERT,

    FORESPOERSEL_MOTTATT,
    FORESPOERSEL_BESVART,
    FORESPOERSEL_FORKASTET,
    FORESPOERSEL_KASTET_TIL_INFOTRYGD,

    SAK_OG_OPPGAVE_OPPRETT_REQUESTED,
    SAK_OG_OPPGAVE_OPPRETTET,
    SAK_OG_OPPGAVE_FERDIGSTILT,
    SAK_OG_OPPGAVE_UTGAATT,

    OPPGAVE_ENDRE_PAAMINNELSE_REQUESTED,
    MANUELL_ENDRE_PAAMINNELSE,
}
