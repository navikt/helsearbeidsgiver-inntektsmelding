package no.nav.helsearbeidsgiver.inntektsmelding.db.river

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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmeldingDokument
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterImLøser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val PERSISTER_IM = BehovType.PERSISTER_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to PERSISTER_IM.name
            )
            it.interestedIn(
                DataFelt.INNTEKTSMELDING,
                DataFelt.VIRKSOMHET,
                DataFelt.ARBEIDSTAKER_INFORMASJON,
                Key.FORESPOERSEL_ID
            )
        }

    override fun onBehov(packet: JsonMessage) {
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $PERSISTER_IM med id $forespørselId")
        sikkerLogger.info("Fikk pakke:\n${packet.toPretty()}")
        try {
            val arbeidsgiver = packet[DataFelt.VIRKSOMHET.str].asText()
            sikkerLogger.info("Fant arbeidsgiver: $arbeidsgiver")
            val arbeidstakerInfo = customObjectMapper().treeToValue(packet[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java)
            val fulltNavn = arbeidstakerInfo.navn
            sikkerLogger.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = customObjectMapper().treeToValue(packet[DataFelt.INNTEKTSMELDING.str], InnsendingRequest::class.java)
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            repository.lagreInntektsmeldng(forespørselId, inntektsmeldingDokument)
            sikkerLogger.info("Lagret InntektsmeldingDokument for forespoerselId: $forespørselId")
            packet[DataFelt.INNTEKTSMELDING_DOKUMENT.str] = inntektsmeldingDokument
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
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                Key.UUID.str to uuid
            )
        )
        publishData(packet)
    }

    private fun publiserFail(fail: Feilmelding, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to customObjectMapper().writeValueAsString(fail),
                Key.UUID.str to jsonMessage[Key.UUID.str].asText()
            )
        )
        super.publishFail(message)
    }
}
