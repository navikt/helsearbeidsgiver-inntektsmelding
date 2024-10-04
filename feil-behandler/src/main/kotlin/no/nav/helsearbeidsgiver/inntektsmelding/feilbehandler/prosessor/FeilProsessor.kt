package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class FeilProsessor(
    private val rapid: RapidsConnection,
) : BakgrunnsjobbProsesserer {
    override val type: String
        get() = JOB_TYPE

    companion object {
        const val JOB_TYPE = "kafka-retry-message"
    }

    private val sikkerLogger = sikkerLogger()

    override fun prosesser(jobb: Bakgrunnsjobb) {
        sikkerLogger.info("Prosesserer jobb - rekjører melding med id ${jobb.uuid}")
        sikkerLogger.debug("Sender melding: ${jobb.data}")
        rapid.publish(jobb.data)
    }
}
