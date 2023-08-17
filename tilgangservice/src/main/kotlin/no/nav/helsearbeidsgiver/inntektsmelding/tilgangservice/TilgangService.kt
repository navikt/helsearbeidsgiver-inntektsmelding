package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
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
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class TilgangService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.TILGANG_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.ORGNRUNDERENHET.str,
                    DataFelt.TILGANG.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.FNR.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .readOrIllegalState("Fant ikke forespørsel-ID.")
            .let(UUID::fromString)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")

            when (transaction) {
                Transaction.NEW -> {
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.HENT_IM_ORGNR.toJson(),
                        DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                        .also {
                            MdcUtils.withLogFields(
                                Log.behov(BehovType.HENT_IM_ORGNR)
                            ) {
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                            }
                        }
                }

                Transaction.IN_PROGRESS -> {
                    val orgnrKey = RedisKey.of(transaksjonId.toString(), DataFelt.ORGNRUNDERENHET)

                    if (isDataCollected(orgnrKey)) {
                        val orgnr = orgnrKey.readOrIllegalState("Fant ikke orgnr.")

                        val fnr = RedisKey.of(transaksjonId.toString(), DataFelt.FNR)
                            .readOrIllegalState("Fant ikke fnr.")

                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                            DataFelt.ORGNRUNDERENHET to orgnr.toJson(),
                            DataFelt.FNR to fnr.toJson(),
                            Key.UUID to transaksjonId.toJson()
                        )
                            .also {
                                MdcUtils.withLogFields(
                                    Log.behov(BehovType.TILGANGSKONTROLL)
                                ) {
                                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                                }
                            }
                    } else {
                        sikkerLogger.error("Transaksjon er underveis, men mangler data. Dette bør aldri skje, ettersom vi kun venter på én datapakke.")
                    }
                }
                else -> {
                    sikkerLogger.error("Støtte på forutsett transaksjonstype: $transaction")
                }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .readOrIllegalState("Fant ikke client-ID.")
            .let(UUID::fromString)

        val tilgang = RedisKey.of(transaksjonId.toString(), DataFelt.TILGANG).read()
        val feil = RedisKey.of(transaksjonId.toString(), Feilmelding("")).read()

        val tilgangJson = TilgangData(
            tilgang = tilgang?.fromJson(Tilgang.serializer()),
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(TilgangData.serializer())

        RedisKey.of(clientId.toString()).write(tilgangJson)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.clientId(clientId)
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

        val feil = RedisKey.of(transaksjonId.toString(), Feilmelding(""))
            .read()

        val feilResponse = TilgangData(
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(TilgangData.serializer())
        if (clientId == null) {
            sikkerLogger.error("$event forsøkt terminert, kunne ikke finne $transaksjonId i redis!")
        }
        RedisKey.of(clientId.toString()).write(feilResponse)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.clientId(clientId.orDefault(transaksjonId))
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }

    override fun onError(feil: Fail): Transaction {
        val transaksjonId = feil.uuid ?: throw IllegalStateException("Feil mangler transaksjon-ID.")

        val manglendeDatafelt = when (feil.behov) {
            BehovType.HENT_IM_ORGNR -> DataFelt.ORGNRUNDERENHET
            BehovType.TILGANGSKONTROLL -> DataFelt.TILGANG
            else -> null
        }

        return if (manglendeDatafelt != null) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, manglendeDatafelt)

            sikkerLogger.error("Mottok feilmelding: '${feilmelding.melding}'")

            val feilKey = RedisKey.of(transaksjonId, feilmelding)

            val feilReport = feilKey.read()
                ?.fromJson(FeilReport.serializer())
                .orDefault(FeilReport())
                .also {
                    it.feil.add(feilmelding)
                }
                .toJson(FeilReport.serializer())

            feilKey.write(feilReport)

            Transaction.TERMINATE
        } else {
            Transaction.IN_PROGRESS
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.readOrIllegalState(feilmelding: String): String =
        read() ?: throw IllegalStateException(feilmelding)
}
