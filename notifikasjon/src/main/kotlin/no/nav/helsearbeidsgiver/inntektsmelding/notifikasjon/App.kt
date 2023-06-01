package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerLogger = sikkerLogger()

fun main() {
    val environment = setUpEnvironment()
    RapidApplication
        .create(System.getenv())
        .createNotifikasjon(buildRedisStore(environment), buildClient(environment), environment.linkUrl)
        .start()
}

fun RapidsConnection.createNotifikasjon(
    redisStore: RedisStore,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    linkUrl: String
): RapidsConnection {
    OpprettSakLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
    ForespørselLagretListener(this)
    OpprettOppgaveLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
    SakFerdigLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
    OppgaveFerdigLøser(this, arbeidsgiverNotifikasjonKlient)
    JournalførtListener(this)
    OpprettSak(this, redisStore)
    return this
}

fun buildClient(environment: Environment): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(environment.notifikasjonUrl) { tokenProvider.getToken() }
}

fun buildRedisStore(environment: Environment): RedisStore {
    sikkerLogger.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
