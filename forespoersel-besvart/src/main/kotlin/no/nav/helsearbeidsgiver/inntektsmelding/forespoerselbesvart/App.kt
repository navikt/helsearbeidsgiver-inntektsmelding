package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.SpinnKlient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-besvart".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val priProducer = PriProducer(Env.Kafka, JsonElement.serializer())

    RapidApplication
        .create(System.getenv())
        .createForespoerselBesvartFraSimba()
        .createForespoerselBesvartFraSpleis(priProducer)
        .createAvsenderSystemLoeser(createSpinnKlient())
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselBesvartFraSimba(): RapidsConnection =
    apply {
        logger.info("Starter ${ForespoerselBesvartFraSimbaLoeser::class.simpleName}...")
        ForespoerselBesvartFraSimbaLoeser(this)
    }

fun RapidsConnection.createForespoerselBesvartFraSpleis(priProducer: PriProducer<JsonElement>): RapidsConnection =
    apply {
        logger.info("Starter ${ForespoerselBesvartFraSpleisLoeser::class.simpleName}...")
        ForespoerselBesvartFraSpleisLoeser(this, priProducer)
    }

fun RapidsConnection.createAvsenderSystemLoeser(spinnKlient: SpinnKlient): RapidsConnection =
    apply {
        logger.info("Starter ${AvsenderSystemLoeser::class.simpleName}...")
        AvsenderSystemLoeser(this, spinnKlient)
    }

fun createSpinnKlient(): SpinnKlient {
    val tokenProvider = OAuth2ClientConfig(Env.azureOAuthEnvironment)

    return SpinnKlient(Env.spinnUrl, HttpClient(Apache5), tokenProvider::getToken)
}
