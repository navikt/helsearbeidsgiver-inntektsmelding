@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import org.slf4j.LoggerFactory

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection, val dokarkivClient: DokArkivClient) : River.PacketListener {

    private val BEHOV = BehovType.JOURNALFOER
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.rejectKey("@løsning")
                it.requireKey("inntektsmelding")
            }
        }.register(this)
    }

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: Inntektsmelding): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        if (inntektsmelding.identitetsnummer.equals("000")) {
            throw Exception("Ukjent feil")
        }
        return dokarkivClient.opprettJournalpost(mapOpprettJournalpostRequest(uuid, inntektsmelding), true, "callId_$uuid").journalpostId
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["@id"].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        try {
            val inntektsmelding = mapInntektsmelding(packet["inntektsmelding"])
            sikkerlogg.info("Skal journalføre $inntektsmelding")
            // Journalfør XML og PDF i joark
            val journalpostId = runBlocking {
                opprettJournalpost(uuid, inntektsmelding)
            }
            // TODO Lag kvittering til Altinn?
            // TODO Lag innboks melding i NAV?
            // TODO Publiser på Kafka (spinn skal lese)?
            packet.setLøsning(BEHOV, JournalpostLøsning(journalpostId))
            context.publish(packet.toJson())
        } catch (ex2: UgyldigFormatException) {
            sikkerlogg.info("Klarte ikke journalføre: feil format!", ex2)
            packet.setLøsning(BEHOV, JournalpostLøsning(error = Feilmelding("Feil format i inntektsmelding")))
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            sikkerlogg.info("Klarte ikke journalføre!", ex)
            packet.setLøsning(BEHOV, JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre")))
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
    }
}
