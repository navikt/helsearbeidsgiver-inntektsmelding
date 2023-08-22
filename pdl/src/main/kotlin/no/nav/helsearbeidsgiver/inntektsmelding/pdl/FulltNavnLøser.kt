@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.PdlHentFullPerson
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate
import kotlin.system.measureTimeMillis

class FulltNavnLøser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val BEHOV = BehovType.FULLT_NAVN

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.requireKeys(Key.IDENTITETSNUMMER)
        }

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            val idtext = behov.uuid().let { if (it.isNullOrEmpty()) null else "id is $it" }
                ?: behov.forespoerselId.let { if (it.isNullOrEmpty()) null else "forespoerselId is $it" }
                ?: " kan ikke finne uuid/forespørselID"
            logger.info("Henter navn for $idtext")
            val identitetsnummer = behov[Key.IDENTITETSNUMMER].asText()
            try {
                val info = runBlocking {
                    hentPersonInfo(identitetsnummer)
                }
                sikkerLogger.info("Fant navn: ${info.navn} og ${info.fødselsdato} for identitetsnummer: $identitetsnummer for $idtext")
                logger.info("Fant navn for id: $idtext")
                publishData(behov.createData(mapOf(DataFelt.ARBEIDSTAKER_INFORMASJON to info)))
            } catch (ex: Exception) {
                logger.error("Klarte ikke hente navn for $idtext")
                sikkerLogger.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message} for $idtext", ex)
                publishFail(behov.createFail("Klarte ikke hente navn"))
            }
        }.also {
            logger.info("FullNavn løser took $it")
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }

    private suspend fun hentPersonInfo(identitetsnummer: String): PersonDato {
        val liste: PdlHentFullPerson.PdlFullPersonliste?
        measureTimeMillis {
            liste = pdlClient.fullPerson(identitetsnummer)?.hentPerson
        }.also {
            logger.info("PDL invocation took $it")
        }
        val fødselsdato: LocalDate? = liste?.foedsel?.firstOrNull()?.foedselsdato
        val fulltNavn = liste?.trekkUtFulltNavn() ?: "Ukjent"
        return PersonDato(fulltNavn, fødselsdato)
    }
}
