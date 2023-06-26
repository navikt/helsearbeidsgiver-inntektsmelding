package no.nav.helsearbeidsgiver.inntektsmelding.trenger

import kotlinx.serialization.KSerializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
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
import no.nav.helsearbeidsgiver.felles.toFeilMessage
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

const val UNDEFINED_FELT: String = "{}"
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

    override fun onError(feil: Fail): Transaction {
        val uuid = feil.uuid!!
        var feilmelding: Feilmelding? = null
        if (feil.behov == BehovType.HENT_TRENGER_IM) {
            feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, datafelt = DataFelt.FORESPOERSEL_SVAR)
            val feilKey = RedisKey.of(uuid, feilmelding)
            val feilReport: FeilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.add(feilmelding)
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
            return Transaction.TERMINATE
        } else if (feil.behov == BehovType.VIRKSOMHET) {
            feilmelding = Feilmelding("Vi klarte ikke å hente virksomhet navn.", datafelt = DataFelt.VIRKSOMHET)
            redisStore.set(RedisKey.of(uuid, DataFelt.VIRKSOMHET), "Ukjent navn")
        } else if (feil.behov == BehovType.FULLT_NAVN) {
            feilmelding = Feilmelding("Vi klarte ikke å hente arbeidstaker informasjon.", datafelt = DataFelt.ARBEIDSTAKER_INFORMASJON)
            redisStore.set(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato("Ukjent navn", null).toJsonStr(PersonDato.serializer()))
        } else if (feil.behov == BehovType.INNTEKT) {
            feilmelding = Feilmelding(
                "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                datafelt = DataFelt.INNTEKT
            )
            redisStore.set(RedisKey.of(uuid, DataFelt.INNTEKT), UNDEFINED_FELT)
        }
        if (feilmelding != null) {
            val feilKey = RedisKey.of(uuid, feilmelding)
            val feilReport: FeilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.add(feilmelding)
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
        }
        return Transaction.IN_PROGRESS
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid = message[Key.UUID.str].asText()
        if (transaction == Transaction.NEW) {
            sikkerLogger().info("Dispatcher HENT_TRENGER_IM for $uuid")
            sikkerLogger().info("${this.javaClass.simpleName} Dispatcher HENT_TRENGER_IM for $uuid")
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer().list()),
                Key.UUID to uuid.toJson(),
                Key.BOOMERANG to mapOf(
                    Key.NESTE_BEHOV.str to listOf(BehovType.PREUTFYLL).toJson(BehovType.serializer()),
                    Key.INITIATE_ID.str to uuid.toJson(),
                    Key.INITIATE_EVENT.str to EventName.TRENGER_REQUESTED.toJson()
                ).toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            message.interestedIn(DataFelt.FORESPOERSEL_SVAR.str)
            if (isDataCollected(*step1data(uuid)) && !message[DataFelt.FORESPOERSEL_SVAR.str].isMissingNode) {
                val forespurtData: TrengerInntekt = redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR))!!.fromJson(TrengerInntekt.serializer())
                logger().info("${this.javaClass.simpleName} Dispatcher VIRKSOMHET for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.VIRKSOMHET).toJson(BehovType.serializer().list()),
                    Key.UUID to uuid.toJson(),
                    DataFelt.ORGNRUNDERENHET to forespurtData.orgnr.toJson()
                )
                logger().info("${this.javaClass.simpleName} dispatcher FULLT_NAVN for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.FULLT_NAVN).toJson(BehovType.serializer().list()),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )
                /*
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.ARBEIDSFORHOLD).toJson(ListSerializer(BehovType.serializer())),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )
*/
                logger().info("${this.javaClass.simpleName} Dispatcher INNTEKT for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.INNTEKT).toJson(BehovType.serializer().list()),
                    Key.UUID to uuid.toJson(),
                    DataFelt.TRENGER_INNTEKT to forespurtData.toJson(TrengerInntekt.serializer())
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transactionId = message[Key.UUID.str].asText()
        val foresporselSvar = redisStore.get(RedisKey.of(transactionId, DataFelt.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())
        val inntekt = redisStore.get(RedisKey.of(transactionId, DataFelt.INNTEKT))?.fromJson(Inntekt.serializer())
        val clientId = redisStore.get(RedisKey.of(transactionId, EventName.valueOf(message[Key.EVENT_NAME.str].asText())))
        val feilReport: FeilReport? = redisStore.get(RedisKey.of(uuid = transactionId, Feilmelding("")))?.fromJson(FeilReport.serializer())
        val trengerData = TrengerData(
            fnr = foresporselSvar?.fnr,
            orgnr = foresporselSvar?.orgnr,
            personDato = redisStore.get(RedisKey.of(transactionId, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java),
            virksomhetNavn = redisStore.get(RedisKey.of(transactionId, DataFelt.VIRKSOMHET)),
            inntekt = redisStore.get(RedisKey.of(transactionId, DataFelt.INNTEKT))?.fromJsonWithUndefined(Inntekt.serializer()),
            fravarsPerioder = foresporselSvar?.sykmeldingsperioder,
            egenmeldingsPerioder = foresporselSvar?.egenmeldingsperioder,
            forespurtData = foresporselSvar?.forespurtData,
            bruttoinntekt = inntekt?.gjennomsnitt(),
            tidligereinntekter = inntekt?.historisk,
            feilReport = feilReport
        )
        val json = trengerData.toJsonStr(TrengerData.serializer())
        println(json)
        redisStore.set(RedisKey.of(clientId!!), json)
    }

    override fun terminate(message: JsonMessage) {
        val transactionId = message[Key.UUID.str].asText()
        val fail = message.toFeilMessage()
        sikkerLogger().info("terminate transaction id $transactionId with evenname ${message[Key.EVENT_NAME.str].asText()}")
        val clientId = redisStore.get(RedisKey.of(transactionId, EventName.valueOf(message[Key.EVENT_NAME.str].asText())))!!
        // @TODO kan vare smartere her. Kan definere feilmeldingen i Feil message istedenfor å hardkode det i TrengerService. Vi også ikke trenger å sende alle andre ikke kritiske feilmeldinger hvis vi har noe kritisk
        val feilReport: FeilReport = redisStore.get(RedisKey.of(uuid = transactionId, Feilmelding("")))!!.fromJson(FeilReport.serializer())

        redisStore.set(RedisKey.of(clientId), TrengerData(feilReport = feilReport).toJsonStr(TrengerData.serializer()))
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR)
    )

    fun <T> String.fromJsonWithUndefined(serializer: KSerializer<T>): T? {
        if (this == UNDEFINED_FELT) return null
        return this.fromJson(serializer)
    }
}
