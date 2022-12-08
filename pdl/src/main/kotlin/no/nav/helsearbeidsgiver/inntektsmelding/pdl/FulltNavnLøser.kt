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
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey(Key.IDENTITETSNUMMER.str)
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val identitetsnummer = packet["identitetsnummer"].asText()

        sikkerlogg.info("Henter navn for identitetsnummer $identitetsnummer")
        logger.info("Løser id ${packet["@id"].asText()}")

        try {
            val fulltNavn = runBlocking {
                hentNavn(identitetsnummer)
            }
            sikkerlogg.info("Fant navn: $fulltNavn for identitetsnummer: $identitetsnummer")
            packet.setLøsning(BEHOV, NavnLøsning(fulltNavn))
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, NavnLøsning(error = Feilmelding("Klarte ikke hente navn")))
            sikkerlogg.error("Det oppstod en feil ved henting av identitetsnummer: $identitetsnummer: ${ex.message}", ex)
            context.publish(packet.toJson())
        }
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
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
