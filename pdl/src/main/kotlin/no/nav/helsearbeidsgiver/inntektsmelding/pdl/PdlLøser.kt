package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.LoggerFactory

class PdlLøser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = Behov.FULLT_NAVN.name

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", listOf(BEHOV))
                it.requireKey("@id")
                it.requireKey("identitetsnummer")
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
            packet.setLøsning(BEHOV, Løsning(fulltNavn))
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, Løsning(error = Feilmelding("Klarte ikke hente navn")))
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

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
