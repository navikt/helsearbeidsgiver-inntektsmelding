@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterImLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

class PersisterImLøser(rapidsConnection: RapidsConnection, val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val PERSISTER_IM = BehovType.PERSISTER_IM
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentArbeidsgiver(session: JsonNode): String {
        return session.get(BehovType.VIRKSOMHET.name)?.get("value")?.asText() ?: "Ukjent"
    }

    fun hentNavn(session: JsonNode): String {
        return session.get(BehovType.FULLT_NAVN.name)?.get("value")?.asText() ?: "Ukjent"
    }

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, PERSISTER_IM)
            it.requireKey(Key.ID.str)
            it.interestedIn(Key.INNTEKTSMELDING.str)
            it.interestedIn(Key.SESSION.str)
            it.interestedIn("virksomhet")
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $PERSISTER_IM med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        val session = packet[Key.SESSION.str]
        sikkerlogg.info("Fant session: $session")
        try {
            val arbeidsgiver = hentArbeidsgiver(session)
            sikkerlogg.info("Fant arbeidsgiver: $arbeidsgiver")
            val fulltNavn = hentNavn(session)
            sikkerlogg.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = customObjectMapper().treeToValue(packet[Key.INNTEKTSMELDING.str], InnsendingRequest::class.java)
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            repository.lagreInntektsmeldng(uuid, inntektsmeldingDokument)
            sikkerlogg.info("Lagret InntektsmeldingDokument for uuid: $uuid") // TODO: lagre / benytte separat id i database?
            packet[Key.INNTEKTSMELDING_DOKUMENT.str] = inntektsmeldingDokument
            publiserLøsning(PersisterImLøsning(value = customObjectMapper().writeValueAsString(inntektsmeldingDokument)), packet)
            publiserOK(uuid, inntektsmeldingDokument)
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: $uuid")
            sikkerlogg.error("Klarte ikke persistere: $uuid", ex)
            publiserLøsning(PersisterImLøsning(error = Feilmelding(melding = "Klarte ikke persistere!")), packet)
        }
    }

    private fun publiserOK(uuid: String, inntektsmeldingDokument: InntektsmeldingDokument) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED,
                Key.DATA.str to "",
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                Key.UUID.str to uuid
            )
        )
        rapidsConnection.publish(packet.toJson())
    }

    private fun publiserInntektsmeldingMottatt(
        inntektsmeldingDokument: no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument,
        uuid: String
    ) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                Key.UUID.str to uuid
            )
        )
        logger.info("Publiserer event ${EventName.INNTEKTSMELDING_MOTTATT} for uuid: $uuid")
        publishEvent(packet)
    }

    fun publiserLøsning(løsning: PersisterImLøsning, packet: JsonMessage) {
        packet.setLøsning(PERSISTER_IM, løsning)
        publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
