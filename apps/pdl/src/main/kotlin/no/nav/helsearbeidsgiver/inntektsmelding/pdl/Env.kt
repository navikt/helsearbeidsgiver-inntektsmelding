package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val pdlScope = "PDL_SCOPE".fromEnv()
    val pdlUrl = "PDL_URL".fromEnv()
}
