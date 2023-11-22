package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.KVITTERING_REQUESTED

    private val logger = logger()

    init {
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(Key.FORESPOERSEL_ID), this, rapidsConnection) }
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(DataFelt.INNTEKTSMELDING_DOKUMENT, DataFelt.EKSTERN_INNTEKTSMELDING),
                event,
                this,
                rapidsConnection,
                redisStore
            )
        }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transactionId: String = message[Key.UUID.str].asText()
        if (transaction == Transaction.NEW) {
            val forespoerselId: String = message[Key.FORESPOERSEL_ID.str].asText()
            logger.info("Sender event: ${event.name} for forespørsel $forespoerselId")
            val msg = JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.HENT_PERSISTERT_IM.name,
                    Key.EVENT_NAME.str to event.name,
                    Key.UUID.str to transactionId,
                    Key.FORESPOERSEL_ID.str to forespoerselId
                )
            ).toJson()
            logger.info("Publiserer melding: $msg")
            rapidsConnection.publish(msg)
        } else {
            logger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid= $transactionId")
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val clientId = redisStore.get(RedisKey.of(transaksjonsId, event))!!.let(UUID::fromString)
        // TODO: Skriv bort fra empty payload hvis mulig
        val im = InnsendtInntektsmelding(
            message[DataFelt.INNTEKTSMELDING_DOKUMENT.str].asText().let { if (it != "{}") it.fromJson(Inntektsmelding.serializer()) else null },
            message[DataFelt.EKSTERN_INNTEKTSMELDING.str].asText().let { if (it != "{}") it.fromJson(EksternInntektsmelding.serializer()) else null }
        ).toJsonStr(InnsendtInntektsmelding.serializer())

        logger.info("Finalize kvittering med transaksjonsId=$transaksjonsId")
        redisStore.set(RedisKey.of(clientId), im)
    }

    override fun terminate(fail: Fail) {
        val transaksjonId = fail.uuid!!.let(UUID::fromString)

        val clientId = redisStore.get(RedisKey.of(transaksjonId, event))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespørselId}")
            }
        } else {
            logger.info("Terminate kvittering med forespoerselId=${fail.forespørselId} og transaksjonsId ${fail.uuid}")
            redisStore.set(RedisKey.of(clientId), fail.feilmelding)
        }
    }
}
