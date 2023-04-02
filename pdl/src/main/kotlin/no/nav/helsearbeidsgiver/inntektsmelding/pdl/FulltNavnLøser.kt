@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.LoggerFactory

class FulltNavnLøser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.FULLT_NAVN

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.ID.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = packet[Key.ID.str].asText()
        logger.info("Henter navn for id: $id")
        val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
        try {
            val fulltNavn = runBlocking {
                hentNavn(identitetsnummer)
            }
            sikkerlogg.info("Fant navn: $fulltNavn for identitetsnummer: $identitetsnummer for id: $id")
            logger.info("Fant navn for id: $id")
            publish(NavnLøsning(fulltNavn), packet, context)
        } catch (ex: Exception) {
            logger.error("Klarte ikke hente navn for id $id")
            sikkerlogg.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message} for id: $id", ex)
            publish(NavnLøsning(error = Feilmelding("Klarte ikke hente navn")), packet, context)
        }
    }

    fun publish(løsning: NavnLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, løsning)
        val json = packet.toJson()
        context.publish(json)
        logger.info("FulltNavnLøser: publiserte: $json")
    }

    suspend fun hentNavn(identitetsnummer: String): String {
        val navn = pdlClient.personNavn(identitetsnummer)?.navn?.firstOrNull()
        return if (navn?.mellomnavn.isNullOrEmpty()) {
            "${navn?.fornavn} ${navn?.etternavn}"
        } else {
            "${navn?.fornavn} ${navn?.mellomnavn} ${navn?.etternavn}"
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
