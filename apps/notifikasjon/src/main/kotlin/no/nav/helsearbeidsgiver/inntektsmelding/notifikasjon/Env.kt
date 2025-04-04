package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.ISO8601Duration
import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val linkUrl = "LINK_URL".fromEnv()
    val agNotifikasjonScope = "ARBEIDSGIVER_NOTIFIKASJON_SCOPE".fromEnv()
    val agNotifikasjonUrl = "ARBEIDSGIVER_NOTIFIKASJON_API_URL".fromEnv()

    val paaminnelseToggle =
        PaaminnelseToggle(
            oppgavePaaminnelseAktivert = "OPPGAVEPAAMINNELSER_AKTIVERT".fromEnv().toBoolean(),
            tidMellomOppgaveopprettelseOgPaaminnelse = "TID_MELLOM_OPPGAVEOPPRETTELSE_OG_PAAMINNELSE".fromEnv(),
        )
}

data class PaaminnelseToggle(
    val oppgavePaaminnelseAktivert: Boolean,
    val tidMellomOppgaveopprettelseOgPaaminnelse: ISO8601Duration,
)
