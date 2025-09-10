package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val brregUrl = "ENHETSREGISTERET_URL".fromEnv()
}
