package no.nav.helsearbeidsgiver.inntektsmelding.eksterntsystem

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.AvsenderSystemData
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.*

class EksterntSystemService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.AVSENDER_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.AVSENDER_SYSTEM_DATA.str
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
                    Key.BEHOV to BehovType.HENT_AVSENDER_SYSTEM.toJson(),
                    DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                    DataFelt.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                    Key.UUID to transaksjonId.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.HENT_AVSENDER_SYSTEM)
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
        val avsenderSystem = message[DataFelt.AVSENDER_SYSTEM_DATA.str]
        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)

        if (avsenderSystem != null && forespoerselId != null) {
            val avsenderSystemData: AvsenderSystemData = avsenderSystem.toString().fromJson(AvsenderSystemData.serializer())

            if (avsenderSystemData.avsenderSystemNavn != "NAV_NO") {
                val msg = JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.name,
                        Key.BEHOV.str to BehovType.LAGRE_AVSENDER_SYSTEM.name,
                        Key.UUID.str to randomUuid(),
                        DataFelt.FORESPOERSEL_ID.str to forespoerselId,
                        DataFelt.AVSENDER_SYSTEM_DATA.str to avsenderSystemData
                    )
                ).toJson()
                // rapid.publish(Behov.create(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT, BehovType.LAGRE_AVSENDER_SYSTEM, forespoerselId.toString(), mapOf(DataFelt.AVSENDER_SYSTEM_DATA to avsenderSystemData)).toJsonMessage().toString())
                rapid.publish(msg)
                /*rapid.publish(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.BEHOV to BehovType.LAGRE_AVSENDER_SYSTEM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                    DataFelt.AVSENDER_SYSTEM_DATA to avsenderSystemData.toJson(AvsenderSystemData.serializer())
                )*/
            }
        }
        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()?.let(UUID::fromString)
        if (clientId == null) {
            sikkerLogger.error("Kunne ikke lese clientId for $transaksjonId fra Redis")
        }

        val logFields = loggFelterNotNull(transaksjonId, clientId)

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

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
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
