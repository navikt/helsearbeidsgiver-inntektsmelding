package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektData
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
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
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.logger
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

class InntektService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.INNTEKT_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.INNTEKT.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.SKJAERINGSTIDSPUNKT.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        MdcUtils.withLogFields(
            "class" to simpleName(),
            "event_name" to event.name,
            "transaksjon_id" to transaksjonId.toString()
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")

            when (transaction) {
                Transaction.NEW -> {
                    val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
                        .readOrIllegalState("Fant ikke forespørsel-ID.")

                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                        DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                        .also {
                            MdcUtils.withLogFields(
                                "forespoersel_id" to forespoerselId
                            ) {
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                            }
                        }
                }

                Transaction.IN_PROGRESS -> {
                    val forspoerselKey = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_SVAR)

                    if (isDataCollected(forspoerselKey)) {
                        val forespoersel = forspoerselKey.readOrIllegalState("Fant ikke svar med forespørsel.")
                            .fromJson(TrengerInntekt.serializer())

                        val skjaeringstidspunkt = RedisKey.of(transaksjonId.toString(), DataFelt.SKJAERINGSTIDSPUNKT)
                            .readOrIllegalState("Fant ikke skjæringstidspunkt.")

                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.INNTEKT.toJson(),
                            DataFelt.TRENGER_INNTEKT to forespoersel.toJson(TrengerInntekt.serializer()),
                            DataFelt.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson(),
                            Key.UUID to transaksjonId.toJson()
                        )
                            .also {
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                            }
                    } else {
                        logger.error("Transaksjon er underveis, men mangler data. Dette bør aldri skje, ettersom vi kun venter på én datapakke.")
                    }
                }
                else -> {
                    logger.error("Støtte på forutsett transaksjonstype: $transaction")
                }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json).toString()

        val clientId = RedisKey.of(transaksjonId, event).readOrIllegalState("Fant ikke client-ID.")
        val inntekt = RedisKey.of(transaksjonId, DataFelt.INNTEKT).read()
        val feil = RedisKey.of(transaksjonId, Feilmelding("")).read()

        val inntektJson = InntektData(
            inntekt = inntekt?.fromJson(Inntekt.serializer()),
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(InntektData.serializer())

        RedisKey.of(clientId).write(inntektJson)

        MdcUtils.withLogFields(
            "class" to simpleName(),
            "event_name" to event.name,
            "transaksjon_id" to transaksjonId,
            "client_id" to clientId
        ) {
            sikkerLogger.info("$event fullført.")
        }
    }

    override fun terminate(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json).toString()

        val clientId = RedisKey.of(transaksjonId, event).readOrIllegalState("Fant ikke client-ID.")
        val feil = RedisKey.of(transaksjonId, Feilmelding("")).readOrIllegalState("Fant ikke feil.")

        val feilResponse = InntektData(
            feil = feil.fromJson(FeilReport.serializer())
        )
            .toJson(InntektData.serializer())

        RedisKey.of(clientId).write(feilResponse)

        MdcUtils.withLogFields(
            "class" to simpleName(),
            "event_name" to event.name,
            "transaksjon_id" to transaksjonId,
            "client_id" to clientId
        ) {
            sikkerLogger.info("$event terminert.")
        }
    }

    override fun onError(feil: Fail): Transaction {
        val transaksjonId = feil.uuid ?: throw IllegalStateException("Feil mangler transaksjon-ID.")

        val (feilmelding, transaction) = when (feil.behov) {
            BehovType.HENT_TRENGER_IM -> {
                val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, DataFelt.FORESPOERSEL_SVAR)

                feilmelding to Transaction.TERMINATE
            }
            BehovType.INNTEKT -> {
                val feilmelding = Feilmelding(
                    "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                    datafelt = DataFelt.INNTEKT
                )

                RedisKey.of(transaksjonId, DataFelt.INNTEKT).write(JsonObject(emptyMap()))

                feilmelding to null
            }
            else -> null to null
        }

        if (feilmelding != null) {
            val feilKey = RedisKey.of(transaksjonId, feilmelding)

            val feilReport = feilKey.read()
                ?.fromJson(FeilReport.serializer())
                .orDefault(FeilReport())
                .also {
                    it.feil.add(feilmelding)
                }
                .toJson(FeilReport.serializer())

            feilKey.write(feilReport)
        }

        return transaction ?: Transaction.IN_PROGRESS
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.readOrIllegalState(feilmelding: String): String =
        read() ?: throw IllegalStateException(feilmelding)
}
