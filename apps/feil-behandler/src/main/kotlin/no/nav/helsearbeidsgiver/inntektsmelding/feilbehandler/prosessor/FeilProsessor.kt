package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class FeilProsessor(
    private val rapid: RapidsConnection,
) : BakgrunnsjobbProsesserer {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val type: String
        get() = JOB_TYPE

    companion object {
        const val JOB_TYPE = "kafka-retry-message"
    }

    override fun prosesser(jobb: Bakgrunnsjobb) {
        "Prosesserer jobb - rekjører melding med ID '${jobb.uuid}'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }
        sikkerLogger.debug("Sender melding.\n${jobb.data.parseJson().toPretty()}")
        rapid.publish(jobb.data)
    }
}
