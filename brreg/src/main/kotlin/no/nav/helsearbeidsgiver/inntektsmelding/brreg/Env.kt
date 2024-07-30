package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val brregUrl = "ENHETSREGISTERET_URL".fromEnv()
}
