package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.InputFelter
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringServiceExperimental(
    override val rapidsConnection: RapidsConnection,
    override val redisStore: RedisStore
) : DefaultEventListenerWithUserInput(
    InputFelter()
        .IN(listOf(Key.FORESPOERSEL_ID.str))
        .OUT(listOf(DataFelter.INNTEKTSMELDING_DOKUMENT.str)),
    redisStore,
    EventName.KVITTERING_REQUESTED,
    rapidsConnection
) {

    init {
        start()
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        if (transaction == Transaction.NEW) {
            val forespørselKey = message[Key.UUID.str].asText() + Key.FORESPOERSEL_ID.str
            val forespoerselId: String = redisStore.get(forespørselKey)!!
            val transactionId: String = message[Key.UUID.str].asText()
            logger.info("Sender event: ${event.name} for forespørsel $forespoerselId")
            val msg = JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to listOf(BehovType.HENT_PERSISTERT_IM.name),
                    Key.EVENT_NAME.str to event.name,
                    Key.UUID.str to transactionId,
                    Key.FORESPOERSEL_ID.str to forespoerselId
                )
            ).toJson()
            logger.info("Publiserer melding: $msg")
            rapidsConnection.publish(msg)
        } else {
            logger.error("Mottok $transaction, skal ikke skje")
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val dok = message[Key.INNTEKTSMELDING_DOKUMENT.str].asText()
        logger.info("Finalize kvittering med transaksjonsId=$transaksjonsId")
        redisStore.set(transaksjonsId, dok)
    }

    override fun terminate(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Terminate kvittering med forespoerselId=$forespoerselId og transaksjonsId $transaksjonsId")
        redisStore.set(transaksjonsId, message[Key.FAIL.str].asText())
    }
}
