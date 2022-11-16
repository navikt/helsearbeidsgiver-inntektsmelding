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
import no.nav.helsearbeidsgiver.felles.Key
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
                it.interestedIn("inntektsmelding")
                it.interestedIn("session")
                it.interestedIn("uuid")
            }
        }.register(this)
    }

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: Inntektsmelding): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        return dokarkivClient.opprettJournalpost(mapOpprettJournalpostRequest(uuid, inntektsmelding), true, "callId_$uuid").journalpostId
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        try {
            val inntektsmelding = mapInntektsmelding(packet["inntektsmelding"])
            val session = packet[Key.SESSION.str]
            sikkerlogg.info("Fant session: $session")
            sikkerlogg.info("Skal journalføre: $inntektsmelding")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmelding) }
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
