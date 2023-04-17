@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

class PersisterImLøser(rapidsConnection: RapidsConnection, val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val PERSISTER_IM = BehovType.PERSISTER_IM
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, PERSISTER_IM)
            it.interestedIn(Key.INNTEKTSMELDING.str)
            it.interestedIn(DataFelt.VIRKSOMHET.str)
            it.interestedIn(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $PERSISTER_IM med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        try {
            val arbeidsgiver = packet[DataFelt.VIRKSOMHET.str].asText()
            sikkerlogg.info("Fant arbeidsgiver: $arbeidsgiver")
            val arbeidstakerInfo = customObjectMapper().treeToValue(packet[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java)
            val fulltNavn = arbeidstakerInfo.navn
            sikkerlogg.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = customObjectMapper().treeToValue(packet[Key.INNTEKTSMELDING.str], InnsendingRequest::class.java)
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            repository.lagreInntektsmeldng(uuid, inntektsmeldingDokument)
            sikkerlogg.info("Lagret InntektsmeldingDokument for uuid: $uuid")
            packet[Key.INNTEKTSMELDING_DOKUMENT.str] = inntektsmeldingDokument
            publiserOK(uuid, inntektsmeldingDokument)
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: $uuid")
            sikkerlogg.error("Klarte ikke persistere: $uuid", ex)
            publiserFail(Feilmelding("Klarte ikke persistere: $uuid"), packet)
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

    fun publiserFail(fail: Feilmelding, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to customObjectMapper().writeValueAsString(fail),
                Key.UUID.str to jsonMessage[Key.UUID.str].asText()
            )
        )
        super.publishFail(message)
    }
}
