package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.log.logger

class InnsendingService(val rapidsConnection: RapidsConnection, override val redisStore: IRedisStore) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.INSENDING_STARTED

    private val logger = logger()

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal { StatefullDataKanal(DataFelter.values().map { it.str }.toTypedArray(), event, it, rapidsConnection, redisStore) }
        withEventListener { InnsendingStartedListener(this, rapidsConnection) }
    }

    class InnsendingStartedListener(mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : DelegatingEventListener(
        mainListener,
        rapidsConnection
    ) {

        override val event: EventName = EventName.INSENDING_STARTED
        override fun accept(): River.PacketValidation {
            return River.PacketValidation {
                it.interestedIn(DataFelt.INNTEKTSMELDING.str)
                it.requireKey(DataFelt.ORGNRUNDERENHET.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
            }
        }
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = "${feil.uuid}${DataFelter.VIRKSOMHET}"
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return Transaction.IN_PROGRESS
        } else if (feil.behov == BehovType.FULLT_NAVN) {
            val fulltNavnKey = "${feil.uuid}${DataFelter.ARBEIDSTAKER_INFORMASJON.str}"
            redisStore.set(fulltNavnKey, customObjectMapper().writeValueAsString(PersonDato("Ukjent person", null)))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str]
        sikkerLogger.info("InnsendingSerice: fikk melding $packet")
        logger.info("InnsendingService $uuid")
        // val transaction: Transaction = startStransactionIfAbsent(packet)
        val transaction: Transaction = determineTransactionState(packet)

        when (transaction) {
            Transaction.NEW -> {
                initialTransactionState(packet)
                dispatchBehov(packet, transaction)
            }
            Transaction.IN_PROGRESS -> dispatchBehov(packet, transaction)
            Transaction.FINALIZE -> finalize(packet)
            Transaction.TERMINATE -> terminate(packet)
        }
    }

    override fun terminate(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid: String = message[Key.UUID.str].asText()
        when (transaction) {
            Transaction.NEW -> {
                logger.info("InnsendingService: emitiing behov Virksomhet")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.VIRKSOMHET.name),
                            DataFelt.ORGNRUNDERENHET.str to message[DataFelt.ORGNRUNDERENHET.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
                logger.info("InnsendingService: emitiing behov ARBEIDSFORHOLD")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.ARBEIDSFORHOLD.name),
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
                logger.info("InnsendingService: emitiing behov FULLT_NAVN")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.FULLT_NAVN.name),
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
            }
            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(message[Key.UUID.str].asText()))) {
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java)
                    logger.info("InnsendingService: emitiing behov PERSISTER_IM")
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.BEHOV.str to listOf(BehovType.PERSISTER_IM.name),
                                DataFelter.VIRKSOMHET.str to (redisStore.get(RedisKey.of(uuid, DataFelt.VIRKSOMHET)) ?: "Ukjent virksomhet"),
                                DataFelter.ARBEIDSTAKER_INFORMASJON.str to (
                                    arbeidstakerRedis ?: PersonDato(
                                        "Ukjent navn",
                                        null
                                    )
                                    ),
                                DataFelt.INNTEKTSMELDING.str to customObjectMapper().readTree(redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING)))!!,
                                Key.FORESPOERSEL_ID.str to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!,
                                Key.UUID.str to uuid
                            )
                        ).toJson()
                    )
                }
            }
            Transaction.FINALIZE -> {
                println("I was not supposed to be hereeeeeeeeeeeeeeeeeeeeeee")
            }
            Transaction.TERMINATE -> {}
        }
    }

    override fun finalize(message: JsonMessage) {
        val uuid: String = message[Key.UUID.str].asText()
        redisStore.set(uuid, redisStore.get(uuid + Key.INNTEKTSMELDING_DOKUMENT.str)!!)
        logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $uuid")
        logger.info("InnsendingService: emitiing event INNTEKTSMELDING_MOTTATT")
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    Key.INNTEKTSMELDING_DOKUMENT.str to message[Key.INNTEKTSMELDING_DOKUMENT.str],
                    Key.TRANSACTION_ORIGIN.str to uuid,
                    DataFelt.FORESPOERSEL_ID.str to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!
                )
            ).toJson().also {
                logger.info("Submitting INNTEKTSMELDING_MOTTATT $it")
            }
        )
    }

    override fun initialTransactionState(message: JsonMessage) {
        val uuid = message.get(Key.UUID.str).asText()
        redisStore.set(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING), message[DataFelt.INNTEKTSMELDING.str])
        redisStore.set(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID), message[Key.FORESPOERSEL_ID.str].asText())
    }
    fun step1data(uuid: String): Array<String> = arrayOf(
        uuid + DataFelter.VIRKSOMHET.str,
        uuid + DataFelter.ARBEIDSFORHOLD.str,
        uuid + DataFelter.ARBEIDSTAKER_INFORMASJON.str
    )
}
