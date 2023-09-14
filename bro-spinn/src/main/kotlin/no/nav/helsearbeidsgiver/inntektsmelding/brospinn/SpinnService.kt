package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class SpinnService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.EKSTERN_INNTEKTSMELDING.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.SPINN_INNTEKTSMELDING_ID.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (forespoerselId == null) {
            publishFail(message)
            return
        }
        val spinnImId = RedisKey.of(transaksjonId.toString(), DataFelt.SPINN_INNTEKTSMELDING_ID)
            .read()?.let(UUID::fromString)
        if (spinnImId == null) {
            publishFail(message)
            return
        }

        val fields = loggFelterNotNull(transaksjonId, forespoerselId)

        MdcUtils.withLogFields(
            *fields
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")

            if (transaction == Transaction.NEW) {
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                    DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                    DataFelt.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                    Key.UUID to transaksjonId.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.HENT_EKSTERN_INNTEKTSMELDING)
                        ) {
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                        }
                    }
            }
        }
    }

    private fun publishFail(message: JsonMessage) {
        rapid.publish(message.createFail("Kunne ikke lese data fra Redis!").toJsonMessage().toJson())
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()
        val transaksjonId = Key.UUID.les(UuidSerializer, json)
        val eksternInntektsmelding = DataFelt.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), json)
        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (
            forespoerselId != null &&
            eksternInntektsmelding?.avsenderSystemNavn != null &&
            eksternInntektsmelding.avsenderSystemNavn != "NAV_NO"
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson(),
                Key.UUID to randomUuid().toJson(),
                DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                DataFelt.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(
                    EksternInntektsmelding.serializer()
                )
            ).also {
                logger.info("Publiserte melding om ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name} for transaksjonId $transaksjonId.")
                sikkerLogger.info("Publiserte melding: ${it.toPretty()}")
            }

        }
        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()?.let(UUID::fromString)
        if (clientId == null) {
            sikkerLogger.error("Kunne ikke lese clientId for $transaksjonId fra Redis")
        }

        val logFields = loggFelterNotNull(transaksjonId, clientId,)

        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.info("$event fullført.")
        }
    }

    override fun terminate(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()
            ?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("$event forsøkt terminert, kunne ikke finne $transaksjonId i redis!")
        }

        val logFields = loggFelterNotNull(transaksjonId, clientId)
        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    // Veldig stygt, ta med clientId i loggfelter når den eksisterer
    // TODO: skriv heller om MDCUtils.log. Fjern dette...
    private fun loggFelterNotNull(
        transaksjonId: UUID,
        clientId: UUID?
    ): Array<Pair<String, String>> {
        val logs = arrayOf(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        )
        val logFields = clientId?.let {
            logs +
                arrayOf(
                    Log.clientId(clientId)
                )
        } ?: logs
        return logFields
    }
}
