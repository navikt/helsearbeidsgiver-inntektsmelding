package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DataFields

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringService3(
    override val rapidsConnection: RapidsConnection,
    override val redisStore: RedisStore
) : DefaultEventListener2(
    DataFields().apply {
        IN.add(Key.FORESPOERSEL_ID.str)
        OUT.add(DataFelter.INNTEKTSMELDING_DOKUMENT.str)
    },
    redisStore,
    EventName.KVITTERING_REQUESTED,
    rapidsConnection
) {

    init {
        start()
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        when (transaction) {
            Transaction.NEW -> {
                val forespoerselId: String = redisStore.get(Key.FORESPOERSEL_ID.str)!!
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
            }
            Transaction.IN_PROGRESS -> {
                logger.error("Mottok ${Transaction.IN_PROGRESS}, skal ikke skje")
            }
            Transaction.FINALIZE -> {
                logger.error("Mottok ${Transaction.FINALIZE}, skal ikke skje")
            }
            Transaction.TERMINATE -> {
                logger.error("Mottok ${Transaction.TERMINATE}, skal ikke skje")
            }
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
