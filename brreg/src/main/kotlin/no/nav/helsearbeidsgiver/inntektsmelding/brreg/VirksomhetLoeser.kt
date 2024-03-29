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
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

class VirksomhetLoeser(
    rapidsConnection: RapidsConnection,
    private val brregClient: BrregClient,
    private val isPreProd: Boolean
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    private val preprodOrgnr = mapOf(
        "810007702" to "ANSTENDIG PIGGSVIN BYDEL",
        "810007842" to "ANSTENDIG PIGGSVIN BARNEHAGE",
        "810008032" to "ANSTENDIG PIGGSVIN BRANNVESEN",
        "810007982" to "ANSTENDIG PIGGSVIN SYKEHJEM"
    )

    private val BEHOV = BehovType.VIRKSOMHET
    private val requestLatency = Summary.build()
        .name("simba_brreg_hent_virksomhet_latency_seconds")
        .help("brreg hent virksomhet latency in seconds")
        .register()

    private fun hentVirksomheter(orgnrListe: List<String>): List<Virksomhet> {
        return if (isPreProd) {
            orgnrListe.map { orgnr -> Virksomhet(preprodOrgnr.getOrDefault(orgnr, "Ukjent arbeidsgiver"), orgnr) }
        } else {
            runBlocking {
                val virksomheterNavn: List<Virksomhet>
                val requestTimer = requestLatency.startTimer()
                measureTimeMillis {
                    virksomheterNavn = brregClient.hentVirksomheter(orgnrListe)
                }.also {
                    logger.info("BREG execution took $it")
                    requestTimer.observeDuration()
                }
                virksomheterNavn.ifEmpty { throw FantIkkeVirksomhetException(orgnrListe.toString()) }
            }
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
        val json = behov.jsonMessage.toJson().parseJson().toMap()

        val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)
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

        logger.info("Løser behov $BEHOV med uuid $transaksjonId")

        try {
            val navnListe: Map<String, String> =
                hentVirksomheter(orgnr)
                    .associate { it.organisasjonsnummer to it.navn }

            rapidsConnection.publishData(
                eventName = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID],
                Key.VIRKSOMHET to navnListe.values.first().toJson(),
                Key.VIRKSOMHETER to navnListe.toJson()
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
