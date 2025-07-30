package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class FeilProsessor(
    private val publisher: Publisher,
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

        val melding = jobb.data.parseJson()

        sikkerLogger.debug("Sender melding.\n${melding.toPretty()}")

        // Bruker 'jobb.uuid' som nøkkel enn så lenge, men bør bruke nøkkelen fra den originale meldingen
        publisher.publish(jobb.uuid, *melding.toMap().toList().toTypedArray())
    }
}
