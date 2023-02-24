@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterImLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import org.slf4j.LoggerFactory

class PersisterImLøser(val rapidsConnection: RapidsConnection, val repository: Repository) : River.PacketListener {

    private val BEHOV = BehovType.PERSISTER_IM
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.ID.str)
                it.rejectKey(Key.LØSNING.str)
                it.interestedIn(Key.INNTEKTSMELDING.str)
                it.interestedIn(Key.SESSION.str)
                it.interestedIn(Key.UUID.str)
            }
        }.register(this)
    }

    fun hentArbeidsgiver(session: JsonNode): String {
        return session.get(BehovType.VIRKSOMHET.name)?.get("value")?.asText() ?: "Ukjent"
    }

    fun hentNavn(session: JsonNode): String {
        return session.get(BehovType.FULLT_NAVN.name)?.get("value")?.asText() ?: "Ukjent"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        val session = packet[Key.SESSION.str]
        sikkerlogg.info("Fant session: $session")
        try {
            val arbeidsgiver = hentArbeidsgiver(session)
            sikkerlogg.info("Fant arbeidsgiver: $arbeidsgiver")
            val fulltNavn = hentNavn(session)
            sikkerlogg.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = packet[Key.INNTEKTSMELDING.str].toJsonElement().fromJson()
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            val dbUuid = repository.lagre(uuid, inntektsmeldingDokument)
            sikkerlogg.info("Lagret InntektsmeldingDokument for uuid: $dbUuid")
            packet[Key.INNTEKTSMELDING_DOKUMENT.str] = inntektsmeldingDokument
//            packet[Key.NESTE_BEHOV.str] = listOf(
//                BehovType.JOURNALFOER.name
//            )
            publiserLøsning(PersisterImLøsning(dbUuid), packet, context)
            publiserInntektsmeldingMottatt(inntektsmeldingDokument)
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: $uuid")
            sikkerlogg.error("Klarte ikke persistere: $uuid", ex)
            publiserLøsning(PersisterImLøsning(error = Feilmelding(melding = "Klarte ikke persistere!")), packet, context)
        }
    }

    private fun publiserInntektsmeldingMottatt(inntektsmeldingDokument: InntektsmeldingDokument) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument
            )
        )
        rapidsConnection.publish(packet.toJson())
    }

    fun publiserLøsning(løsning: PersisterImLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
