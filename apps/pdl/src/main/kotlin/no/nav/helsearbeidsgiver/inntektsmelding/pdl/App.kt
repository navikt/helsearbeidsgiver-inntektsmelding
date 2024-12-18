package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-pdl".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createPdlRiver(buildClient())
        .start()
}

fun RapidsConnection.createPdlRiver(pdlClient: PdlClient): RapidsConnection =
    also {
        logger.info("Starter ${HentPersonerRiver::class.simpleName}...")
        HentPersonerRiver(pdlClient).connect(this)
    }

fun buildClient(): PdlClient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return PdlClient(Env.pdlUrl, Behandlingsgrunnlag.INNTEKTSMELDING, tokenGetter)
}
