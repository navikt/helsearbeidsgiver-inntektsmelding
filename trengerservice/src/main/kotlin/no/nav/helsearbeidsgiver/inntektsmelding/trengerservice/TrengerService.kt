package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

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
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

const val UNDEFINED_FELT: String = "{}"
class TrengerService(private val rapidsConnection: RapidsConnection, override val redisStore: IRedisStore) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.TRENGER_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal {
            StatefullDataKanal(
                listOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str,
                    DataFelt.ARBEIDSGIVER_INFORMASJON.str,
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
            redisStore.set(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer()))
            redisStore.set(RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_INFORMASJON), PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer()))
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
        sikkerLogger.info("Dispatcher for $uuid with trans state $transaction")
        if (transaction == Transaction.NEW) {
            sikkerLogger.info("Dispatcher HENT_TRENGER_IM for $uuid")
            sikkerLogger.info("${simpleName()} Dispatcher HENT_TRENGER_IM for $uuid")
            val agFnr = message[Key.ARBEIDSGIVER_ID.str].asText()
            redisStore.set(RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_FNR), agFnr) // ta vare på denne til vi slår opp fullt navn
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson(),
                Key.UUID to uuid.toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            message.interestedIn(DataFelt.FORESPOERSEL_SVAR.str)
            if (isDataCollected(*step1data(uuid)) && !message[DataFelt.FORESPOERSEL_SVAR.str].isMissingNode) {
                val forespoersel = redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR))!!.fromJson(TrengerInntekt.serializer())

                sikkerLogger.info("${simpleName()} Dispatcher VIRKSOMHET for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                    Key.UUID to uuid.toJson(),
                    DataFelt.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
                )
                sikkerLogger.info("${simpleName()} dispatcher FULLT_NAVN for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespoersel.fnr.toJson(),
                    Key.ARBEIDSGIVER_ID to redisStore.get(RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_FNR)).orEmpty().toJson()
                )
                /*
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )
                */

                val skjaeringstidspunkt = forespoersel.skjaeringstidspunkt
                    ?: finnSkjaeringstidspunkt(forespoersel.egenmeldingsperioder + forespoersel.sykmeldingsperioder)

                if (skjaeringstidspunkt != null) {
                    sikkerLogger.info("${simpleName()} Dispatcher INNTEKT for $uuid")
                    rapidsConnection.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.INNTEKT.toJson(),
                        Key.UUID to uuid.toJson(),
                        DataFelt.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                        DataFelt.FNR to forespoersel.fnr.toJson(),
                        DataFelt.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson()
                    )
                } else {
                    val forespoerselId = redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))

                    "Fant ikke skjaeringstidspunkt å hente inntekt for.".also {
                        sikkerLogger.error("$it forespoersel=$forespoersel")
                        val feil = Fail(event, BehovType.INNTEKT, it, null, uuid, forespoerselId)
                        onError(feil)
                    }
                }
            }
        } else {
            sikkerLogger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid= $uuid")
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
            arbeidsgiver = redisStore.get(RedisKey.of(transactionId, DataFelt.ARBEIDSGIVER_INFORMASJON), PersonDato::class.java),
            virksomhetNavn = redisStore.get(RedisKey.of(transactionId, DataFelt.VIRKSOMHET)),
            inntekt = redisStore.get(RedisKey.of(transactionId, DataFelt.INNTEKT))?.fromJsonWithUndefined(Inntekt.serializer()),
            fravarsPerioder = foresporselSvar?.sykmeldingsperioder,
            egenmeldingsPerioder = foresporselSvar?.egenmeldingsperioder,
            forespurtData = foresporselSvar?.forespurtData,
            bruttoinntekt = inntekt?.gjennomsnitt(),
            tidligereinntekter = inntekt?.maanedOversikt,
            feilReport = feilReport
        )
        val json = trengerData.toJsonStr(TrengerData.serializer())
        redisStore.set(RedisKey.of(clientId!!), json)
    }

    override fun terminate(message: JsonMessage) {
        val transactionId = message[Key.UUID.str].asText()
        sikkerLogger.info("terminate transaction id $transactionId with evenname ${message[Key.EVENT_NAME.str].asText()}")
        val clientId: String? = redisStore.get(RedisKey.of(transactionId, EventName.valueOf(message[Key.EVENT_NAME.str].asText())))
        // @TODO kan vare smartere her. Kan definere feilmeldingen i Feil message istedenfor å hardkode det i TrengerService. Vi også ikke trenger å sende alle andre ikke kritiske feilmeldinger hvis vi har noe kritisk
        val feilReport: FeilReport = redisStore.get(RedisKey.of(uuid = transactionId, Feilmelding("")))!!.fromJson(FeilReport.serializer())
        if (clientId != null) {
            redisStore.set(RedisKey.of(clientId), TrengerData(feilReport = feilReport).toJsonStr(TrengerData.serializer()))
        }
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR)
    )

    fun <T> String.fromJsonWithUndefined(serializer: KSerializer<T>): T? {
        if (this == UNDEFINED_FELT) return null
        return this.fromJson(serializer)
    }
}
