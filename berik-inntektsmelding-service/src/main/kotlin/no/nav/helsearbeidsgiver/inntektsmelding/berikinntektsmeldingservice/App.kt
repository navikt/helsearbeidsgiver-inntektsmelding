package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-berik-inntektsmelding-service".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createBerikInntektsmeldingService()
        .start()
}

fun RapidsConnection.createBerikInntektsmeldingService(): RapidsConnection =
    also {
        logger.info("Starter ${BerikInntektsmeldingService::class.simpleName}...")
        ServiceRiverStateless(
            BerikInntektsmeldingService(this),
        ).connect(this)
    }
