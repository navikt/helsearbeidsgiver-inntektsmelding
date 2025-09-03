package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val pdlScope = "PDL_SCOPE".fromEnv()
    val pdlUrl = "PDL_URL".fromEnv()
}
