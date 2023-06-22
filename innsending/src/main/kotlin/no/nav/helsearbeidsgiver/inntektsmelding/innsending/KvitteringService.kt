package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.KVITTERING_REQUESTED

    private val logger = logger()

    init {
        logger.info("Starter kvitteringservice")
        withEventListener { StatefullEventListener(redisStore,event, arrayOf(Key.FORESPOERSEL_ID.str),this, rapidsConnection)}
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
        withDataKanal { StatefullDataKanal(arrayOf(DataFelter.INNTEKTSMELDING_DOKUMENT.str), event, this, rapidsConnection, redisStore) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        when (transaction) {
            Transaction.NEW -> {
                val forespoerselId: String = message[Key.FORESPOERSEL_ID.str].asText()
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
        val clientId = redisStore.get(RedisKey.of(transaksjonsId, event))
        val dok = message[DataFelt.INNTEKTSMELDING_DOKUMENT.str].asText()
        logger.info("Finalize kvittering med transaksjonsId=$transaksjonsId")
        redisStore.set(clientId!!, dok)
    }

    override fun terminate(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Terminate kvittering med forespoerselId=$forespoerselId og transaksjonsId $transaksjonsId")
        redisStore.set(transaksjonsId, message[Key.FAIL.str].asText())
    }

}
