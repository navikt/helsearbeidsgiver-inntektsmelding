package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.days

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
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.pdlScope)
    return PdlClient(
        url = Env.pdlUrl,
        behandlingsgrunnlag = Behandlingsgrunnlag.INNTEKTSMELDING,
        cacheConfig = LocalCache.Config(1.days, 10_000),
        getAccessToken = tokenGetter,
    )
}
