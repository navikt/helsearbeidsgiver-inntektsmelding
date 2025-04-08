package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val dokArkivScope = "DOKARKIV_SCOPE".fromEnv()
    val dokArkivUrl = "DOKARKIV_URL".fromEnv()
}
