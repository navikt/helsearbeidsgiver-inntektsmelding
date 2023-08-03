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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.log.logger
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

    override fun onBehov(packet: JsonMessage) {
        measureTimeMillis {
            val idtext = packet[Key.UUID.str].asText().let { if (it.isNullOrEmpty()) null else "id is $it" }
                ?: packet[Key.FORESPOERSEL_ID.str].asText().let { if (it.isNullOrEmpty()) null else "forespoerselId is $it" }
                ?: " kan ikke finne uuid/forespørselID"
            logger.info("Henter navn for $idtext")
            val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
            try {
                val person = hentPerson(identitetsnummer)

                sikkerLogger.info("Fant navn: ${person.navn} og ${person.fødselsdato} for identitetsnummer: $identitetsnummer for $idtext")
                logger.info("Fant navn for id: $idtext")

                publishDatagram(person, packet)
            } catch (ex: Exception) {
                logger.error("Klarte ikke hente navn for $idtext")
                sikkerLogger.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message} for $idtext", ex)
                publishFail(packet.createFail("Klarte ikke hente navn", behovType = BehovType.FULLT_NAVN))
            }
        }.also {
            logger.info("FullNavn løser took $it")
        }
    }

    private fun publishDatagram(personInformasjon: PersonDato, jsonMessage: JsonMessage) {
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

    private fun hentPerson(identitetsnummer: String): PersonDato =
        runBlocking {
            pdlClient.fullPerson(identitetsnummer).let {
                PersonDato(
                    navn = it?.navn?.fulltNavn() ?: "Ukjent",
                    fødselsdato = it?.foedselsdato
                )
            }
        }
}
