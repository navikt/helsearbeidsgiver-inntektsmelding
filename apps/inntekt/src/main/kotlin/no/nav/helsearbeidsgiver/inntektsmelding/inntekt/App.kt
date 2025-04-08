package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntekt".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createHentInntektRiver(createInntektKlient())
        .start()
}

fun RapidsConnection.createHentInntektRiver(inntektKlient: InntektKlient): RapidsConnection =
    also {
        logger.info("Starter ${HentInntektRiver::class.simpleName}...")
        HentInntektRiver(inntektKlient).connect(this)
    }

fun createInntektKlient(): InntektKlient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.inntektScope)
    return InntektKlient(Env.inntektUrl, tokenGetter)
}
