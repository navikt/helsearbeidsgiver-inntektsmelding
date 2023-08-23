@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.system.measureTimeMillis

class VirksomhetLøser(
    rapidsConnection: RapidsConnection,
    private val brregClient: BrregClient,
    private val isPreProd: Boolean
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val BEHOV = BehovType.VIRKSOMHET
    private val requestLatency = Summary.build()
        .name("simba_brreg_hent_virksomhet_latency_seconds")
        .help("brreg hent virksomhet latency in seconds")
        .register()

    private fun hentVirksomhet(orgnr: String): String {
        if (isPreProd) {
            when (orgnr) {
                "810007702" -> return "ANSTENDIG PIGGSVIN BYDEL"
                "810007842" -> return "ANSTENDIG PIGGSVIN BARNEHAGE"
                "810008032" -> return "ANSTENDIG PIGGSVIN BRANNVESEN"
                "810007982" -> return "ANSTENDIG PIGGSVIN SYKEHJEM"
            }
            return "Ukjent arbeidsgiver"
        }
        return runBlocking {
            val virksomhetNav: String?
            val requestTimer = requestLatency.startTimer()
            measureTimeMillis {
                virksomhetNav = brregClient.hentVirksomhetNavn(orgnr)
            }.also {
                logger.info("BREG execution took $it")
                requestTimer.observeDuration()
            }
            virksomhetNav
        } ?: throw FantIkkeVirksomhetException(orgnr)
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.requireKeys(
                DataFelt.ORGNRUNDERENHET,
                Key.UUID
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov $BEHOV med uuid ${behov.uuid()}")
        val orgnr = behov[DataFelt.ORGNRUNDERENHET].asText()
        try {
            val navn = hentVirksomhet(orgnr)
            logger.info("Fant $navn for $orgnr")
            publishData(behov.createData(mapOf(DataFelt.VIRKSOMHET to navn)))
        } catch (ex: FantIkkeVirksomhetException) {
            logger.error("Fant ikke virksomhet for $orgnr")
            publishFail(behov.createFail("Ugyldig virksomhet $orgnr"))
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting for $orgnr")
            sikkerLogger.error("Det oppstod en feil ved henting for orgnr $orgnr: ", ex)
            publishFail(behov.createFail("Klarte ikke hente virksomhet"))
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }
}
