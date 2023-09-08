@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import kotlin.system.measureTimeMillis

class FulltNavnLøser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val BEHOV = BehovType.FULLT_NAVN
    private val requestLatency = Summary.build()
        .name("simba_pdl_latency_seconds")
        .help("pdl kall latency in seconds")
        .register()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.requireKeys(Key.IDENTITETSNUMMER)
            it.interestedIn(Key.ARBEIDSGIVER_ID)
        }

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            val idtext = behov.uuid().let { if (it.isNullOrEmpty()) null else "id is $it" }
                ?: behov.forespoerselId.let { if (it.isNullOrEmpty()) null else "forespoerselId is $it" }
                ?: " kan ikke finne uuid/forespørselID"
            logger.info("Henter navn for $idtext")
            val arbeidstakerID = behov[Key.IDENTITETSNUMMER].asText().orEmpty()
            val arbeidsgiverID = behov[Key.ARBEIDSGIVER_ID].asText().orEmpty()
            val identer = listOf(arbeidstakerID, arbeidsgiverID).filterNot { s -> s.isNullOrEmpty() }
            val requestTimer = requestLatency.startTimer()
            try {
                val personer = hentPersoner(identer)

                logger.info("Mottok ${personer.size} navn fra pdl, ba om ${identer.size}")

                val arbeidstakerInfo = personer.firstOrNull { it.ident == arbeidstakerID }.orDefault(PersonDato("", null, arbeidstakerID))
                val arbeidsgiverInfo = personer.firstOrNull { it.ident == arbeidsgiverID }.orDefault(PersonDato("", null, arbeidsgiverID))

                publishData(
                    behov.createData(
                        mapOf(
                            DataFelt.ARBEIDSTAKER_INFORMASJON to arbeidstakerInfo,
                            DataFelt.ARBEIDSGIVER_INFORMASJON to arbeidsgiverInfo
                        )
                    )
                )
            } catch (ex: Exception) {
                logger.error("Klarte ikke hente navn for $idtext")
                sikkerLogger.error("Det oppstod en feil ved henting av identitetsnummer: $arbeidstakerID: ${ex.message} for $idtext", ex)
                publishFail(behov.createFail("Klarte ikke hente navn"))
            } finally {
                requestTimer.observeDuration()
            }
        }.also {
            logger.info("FullNavn løser took $it")
        }
    }

    private fun hentPersoner(identitetsnummere: List<String>): List<PersonDato> =
        runBlocking {
            pdlClient.personBolk(identitetsnummere)
        }
            .orEmpty()
            .map {
                PersonDato(
                    navn = it.navn.fulltNavn(),
                    fødselsdato = it.foedselsdato,
                    ident = it.ident.orEmpty()
                )
            }
}
