package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class TilgangService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.TILGANG_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    Key.ORGNRUNDERENHET,
                    Key.TILGANG
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(Key.FORESPOERSEL_ID, Key.FNR), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId, Key.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (forespoerselId == null) {
            "Klarte ikke finne forespoerselId for transaksjon $transaksjonId i Redis.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        } else {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                dispatch(transaction, transaksjonId, forespoerselId)
            }
        }
    }

    private fun dispatch(transaction: Transaction, transaksjonId: UUID, forespoerselId: UUID) {
        sikkerLogger.info("Prosesserer transaksjon $transaction.")

        when (transaction) {
            Transaction.NEW -> {
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.HENT_IM_ORGNR.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
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
                val orgnrKey = RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET)

                if (isDataCollected(orgnrKey)) {
                    val orgnr = orgnrKey.read()

                    val fnr = RedisKey.of(transaksjonId, Key.FNR)
                        .read()
                    if (orgnr == null || fnr == null) {
                        "Klarte ikke lese orgnr og / eller fnr fra Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        return
                    }
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                        Key.ORGNRUNDERENHET to orgnr.toJson(),
                        Key.FNR to fnr.toJson(),
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

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId, event)
            .read()?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("Kunne ikke lese clientId for $transaksjonId fra Redis")
        } else {
            val tilgang = RedisKey.of(transaksjonId, Key.TILGANG).read()
            val feil = RedisKey.of(transaksjonId, Feilmelding("")).read()

            val tilgangJson = TilgangData(
                tilgang = tilgang?.fromJson(Tilgang.serializer()),
                feil = feil?.fromJson(FeilReport.serializer())
            )
                .toJson(TilgangData.serializer())

            RedisKey.of(clientId).write(tilgangJson)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.info("$event fullført.")
            }
        }
    }

    override fun terminate(fail: Fail) {
        val clientId = RedisKey.of(fail.transaksjonId!!, event)
            .read()
            ?.let(UUID::fromString)

        val feil = RedisKey.of(fail.transaksjonId!!, Feilmelding(""))
            .read()

        val feilResponse = TilgangData(
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(TilgangData.serializer())

        if (clientId == null) {
            sikkerLogger.error("$event forsøkt terminert, kunne ikke finne ${fail.transaksjonId} i redis!")
        } else {
            RedisKey.of(clientId).write(feilResponse)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(fail.transaksjonId!!)
            ) {
                sikkerLogger.error("$event terminert.")
            }
        }
    }

    override fun onError(feil: Fail): Transaction {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), feil.utloesendeMelding.toMap())

        val manglendeDatafelt = when (utloesendeBehov) {
            BehovType.HENT_IM_ORGNR -> Key.ORGNRUNDERENHET
            BehovType.TILGANGSKONTROLL -> Key.TILGANG
            else -> null
        }

        if (manglendeDatafelt != null) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, manglendeDatafelt)

            sikkerLogger.error("Mottok feilmelding: '${feilmelding.melding}'")

            val feilKey = RedisKey.of(feil.transaksjonId!!, feilmelding)

            val feilReport = feilKey.read()
                ?.fromJson(FeilReport.serializer())
                .orDefault(FeilReport())
                .also {
                    it.feil.add(feilmelding)
                }
                .toJson(FeilReport.serializer())

            feilKey.write(feilReport)
        }

        return Transaction.TERMINATE
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)
}
