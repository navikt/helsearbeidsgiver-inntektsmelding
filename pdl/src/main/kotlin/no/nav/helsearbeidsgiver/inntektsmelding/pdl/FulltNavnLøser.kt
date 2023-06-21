@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
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
    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.requireKey(Key.IDENTITETSNUMMER.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        measureTimeMillis {
            val idtext = packet[Key.UUID.str].asText().let { if (it.isNullOrEmpty()) null else "id is $it" }
                ?: packet[Key.FORESPOERSEL_ID.str].asText().let { if (it.isNullOrEmpty()) null else "forespoerselId is $it" }
                ?: " kan ikke finne uuid/forespørselID"
            logger.info("Henter navn for $idtext")
            sikkerLogger.info("Henter navn for $idtext")
            val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
            try {
                val info = runBlocking {
                    hentPersonInfo(identitetsnummer)
                }
                sikkerLogger.info("Fant navn: ${info.navn} og ${info.fødselsdato} for identitetsnummer: $identitetsnummer for $idtext")
                logger.info("Fant navn for id: $idtext")
                publish(NavnLøsning(info), packet)
                publishDatagram(info, packet)
            } catch (ex: Exception) {
                logger.error("Klarte ikke hente navn for $idtext")
                sikkerLogger.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message} for $idtext", ex)
                publish(NavnLøsning(error = Feilmelding("Klarte ikke hente navn")), packet)
                publishFail(packet.createFail("Klarte ikke hente navn", behovType = BehovType.FULLT_NAVN))
            }
        }.also {
            logger.info("FullNavn løser took $it")
        }
    }

    private fun publish(løsning: NavnLøsning, packet: JsonMessage) {
        packet.setLøsning(BEHOV, løsning)
        val json = packet.toJson()
        super.publishBehov(packet)
        sikkerLogger.info("FulltNavnLøser: publiserte: $json")
    }

    private fun publishDatagram(personInformasjon: PersonDato, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.DATA.str to "",
                Key.UUID.str to jsonMessage[Key.UUID.str].asText(),
                DataFelt.ARBEIDSTAKER_INFORMASJON.str to personInformasjon,
                Key.FORESPOERSEL_ID.str to jsonMessage[Key.FORESPOERSEL_ID.str].asText()
            )
        )
        super.publishData(message)
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

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
