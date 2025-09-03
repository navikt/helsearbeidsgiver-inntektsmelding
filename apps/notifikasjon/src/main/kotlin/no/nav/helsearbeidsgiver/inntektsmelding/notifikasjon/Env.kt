package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val linkUrl = "LINK_URL".fromEnv()
    val agNotifikasjonScope = "ARBEIDSGIVER_NOTIFIKASJON_SCOPE".fromEnv()
    val agNotifikasjonUrl = "ARBEIDSGIVER_NOTIFIKASJON_API_URL".fromEnv()
    val tidMellomOppgaveOpprettelseOgPaaminnelse = "TID_MELLOM_OPPGAVEOPPRETTELSE_OG_PAAMINNELSE".fromEnv()
}
