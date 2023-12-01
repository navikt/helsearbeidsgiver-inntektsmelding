@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import kotlin.system.measureTimeMillis

class VirksomhetLoeser(
    rapidsConnection: RapidsConnection,
    private val brregClient: BrregClient,
    private val isPreProd: Boolean
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

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
    private fun hentVirksomheter(orgnrListe: List<String>): List<Virksomhet> {
        return runBlocking {
            val virksomheterNavn: List<Virksomhet>
            val requestTimer = requestLatency.startTimer()
            measureTimeMillis {
                virksomheterNavn = brregClient.hentVirksomheter(orgnrListe)
            }.also {
                logger.info("BREG execution took $it")
                requestTimer.observeDuration()
            }
            virksomheterNavn
        }
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.requireKeys(
                Key.UUID
            )
            it.interestedIn(
                Key.ORGNRUNDERENHET,
                Key.ORGNRUNDERENHETER
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov $BEHOV med uuid ${behov.uuid()}")
        val orgnr: List<String> =
            if (behov[Key.ORGNRUNDERENHETER].isEmpty) {
                listOf(
                    behov[Key.ORGNRUNDERENHET]
                        .asText()
                )
            } else {
                behov[Key.ORGNRUNDERENHETER]
                    .map { it.asText() }
            }
        try {
            val navnListe: Map<String, String> =
                hentVirksomheter(orgnr)
                    .map {
                        it.organisasjonsnummer to it.navn
                    }
                    .toMap()
            publishData(
                behov.createData(
                    mapOf(
                        Key.VIRKSOMHET to navnListe.values.first(),
                        Key.VIRKSOMHETER to navnListe
                    )
                )
            )
        } catch (ex: FantIkkeVirksomhetException) {
            logger.error("Fant ikke virksomhet for $orgnr")
            publishFail(behov.createFail("Fant ikke virksomhet"))
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting for $orgnr")
            sikkerLogger.error("Det oppstod en feil ved henting for orgnr $orgnr: ", ex)
            publishFail(behov.createFail("Klarte ikke hente virksomhet"))
        }
    }
}
