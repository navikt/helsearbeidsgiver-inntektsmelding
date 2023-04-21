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
import no.nav.helsearbeidsgiver.felles.publishFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

class FulltNavnLøser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : Løser(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.FULLT_NAVN
    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.requireKey(Key.IDENTITETSNUMMER.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val id = packet[Key.ID.str].asText()
        logger.info("Henter navn for id: $id")
        val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
        try {
            val info = runBlocking {
                hentPersonInfo(identitetsnummer)
            }
            sikkerlogg.info("Fant navn: ${info.navn} og ${info.fødselsdato} for identitetsnummer: $identitetsnummer for id: $id")
            logger.info("Fant navn for id: $id")
            publish(NavnLøsning(info), packet)
            publishDatagram(info, packet)
        } catch (ex: Exception) {
            logger.error("Klarte ikke hente navn for id $id")
            sikkerlogg.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message} for id: $id", ex)
            publish(NavnLøsning(error = Feilmelding("Klarte ikke hente navn")), packet)
            publishFail(packet.createFail("Klarte ikke hente navn", behoveType = BehovType.FULLT_NAVN))
        }
    }

    fun publish(løsning: NavnLøsning, packet: JsonMessage) {
        packet.setLøsning(BEHOV, løsning)
        val json = packet.toJson()
        super.publishBehov(packet)
        sikkerlogg.info("FulltNavnLøser: publiserte: $json")
    }

    fun publishDatagram(personInformasjon: PersonDato, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.DATA.str to "",
                Key.UUID.str to jsonMessage[Key.UUID.str].asText(),
                DataFelt.ARBEIDSTAKER_INFORMASJON.str to personInformasjon
            )
        )
        super.publishData(message)
    }

    suspend fun hentPersonInfo(identitetsnummer: String): PersonDato {
        val liste = pdlClient.fullPerson(identitetsnummer)?.hentPerson
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
