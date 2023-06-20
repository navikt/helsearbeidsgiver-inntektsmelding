package no.nav.helsearbeidsgiver.inntektsmelding.trenger

import kotlinx.serialization.builtins.ListSerializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class TrengerService(private val rapidsConnection: RapidsConnection, override val redisStore: IRedisStore) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.TRENGER_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal {
            StatefullDataKanal(
                listOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str,
                    DataFelt.VIRKSOMHET.str,
                    DataFelt.ARBEIDSFORHOLD.str,
                    DataFelt.INNTEKT.str
                ).toTypedArray(),
                event,
                it,
                rapidsConnection,
                redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, listOf(DataFelt.FORESPOERSEL_ID.str).toTypedArray(), it, rapidsConnection) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid = message[Key.UUID.str].asText()
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to uuid.toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson(),
                Key.BOOMERANG to mapOf(
                    Key.NESTE_BEHOV.str to listOf(BehovType.PREUTFYLL).toJson(BehovType.serializer()),
                    Key.INITIATE_ID.str to uuid.toJson(),
                    Key.INITIATE_EVENT.str to EventName.TRENGER_REQUESTED.toJson()
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            message.interestedIn(DataFelt.FORESPOERSEL_SVAR.str)
            if (isDataCollected(*step1data(uuid)) && !message[DataFelt.FORESPOERSEL_SVAR.str].isMissingNode) {
                val forespurtData: TrengerInntekt = redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR))!!.fromJson(TrengerInntekt.serializer())

                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.VIRKSOMHET).toJson(ListSerializer(BehovType.serializer())),
                    Key.UUID to uuid.toJson(),
                    DataFelt.ORGNRUNDERENHET to forespurtData.orgnr.toJson()
                )

                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.FULLT_NAVN).toJson(ListSerializer(BehovType.serializer())),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )

                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.ARBEIDSFORHOLD).toJson(ListSerializer(BehovType.serializer())),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )

                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.INNTEKT).toJson(ListSerializer(BehovType.serializer())),
                    Key.UUID to uuid.toJson(),
                    DataFelt.TRENGER_INNTEKT to forespurtData.toJson(TrengerInntekt.serializer())
                )
            }
            println(message.toJson())
            println("Heeeelllllloooooooooooooo!!!!!")
        }
    }

    override fun finalize(message: JsonMessage) {
        val transactionId = message[Key.UUID.str].asText()
        val foresporselSvar = redisStore.get(RedisKey.of(transactionId, DataFelt.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())
        val inntekt = redisStore.get(RedisKey.of(transactionId, DataFelt.INNTEKT))?.fromJson(Inntekt.serializer())
        val clientId = redisStore.get(RedisKey.of(transactionId, EventName.valueOf(message[Key.EVENT_NAME.str].asText())))
        val trengerData = TrengerData(
            personDato = redisStore.get(RedisKey.of(transactionId, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java),
            virksomhetNavn = redisStore.get(RedisKey.of(transactionId, DataFelt.VIRKSOMHET)),
            intekt = redisStore.get(RedisKey.of(transactionId, DataFelt.INNTEKT))?.fromJson(Inntekt.serializer()),
            fravarsPerioder = foresporselSvar?.sykmeldingsperioder,
            egenmeldingsPerioder = foresporselSvar?.egenmeldingsperioder,
            forespurtData = foresporselSvar?.forespurtData,
            bruttoinntekt = inntekt?.gjennomsnitt(),
            tidligereinntekter = inntekt?.historisk
        )
        val json = trengerData.toJsonStr(TrengerData.serializer())
        println(json)
        redisStore.set(RedisKey.of(clientId!!), json)
    }

    override fun terminate(message: JsonMessage) {
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR)
    )
}
