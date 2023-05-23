@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterImLøser(rapidsConnection: RapidsConnection, val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val PERSISTER_IM = BehovType.PERSISTER_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, PERSISTER_IM)
            it.interestedIn(Key.INNTEKTSMELDING.str)
            it.interestedIn(DataFelt.VIRKSOMHET.str)
            it.interestedIn(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $PERSISTER_IM med id $forespørselId")
        sikkerLogger.info("Fikk pakke: ${packet.toJson()}")
        try {
            val arbeidsgiver = packet[DataFelt.VIRKSOMHET.str].asText()
            sikkerLogger.info("Fant arbeidsgiver: $arbeidsgiver")
            val arbeidstakerInfo = customObjectMapper().treeToValue(packet[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java)
            val fulltNavn = arbeidstakerInfo.navn
            sikkerLogger.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = customObjectMapper().treeToValue(packet[Key.INNTEKTSMELDING.str], InnsendingRequest::class.java)
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            repository.lagreInntektsmeldng(forespørselId, inntektsmeldingDokument)
            sikkerLogger.info("Lagret InntektsmeldingDokument for forespoerselId: $forespørselId")
            packet[Key.INNTEKTSMELDING_DOKUMENT.str] = inntektsmeldingDokument
            publiserOK(uuid, inntektsmeldingDokument)
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: $forespørselId")
            sikkerLogger.error("Klarte ikke persistere: $forespørselId", ex)
            publiserFail(Feilmelding("Klarte ikke persistere: $forespørselId"), packet)
        }
    }

    private fun publiserOK(uuid: String, inntektsmeldingDokument: InntektsmeldingDokument) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.DATA.str to "",
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                Key.UUID.str to uuid
            )
        )
        publishData(packet)
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
