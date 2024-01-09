package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TilgangService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.TILGANG_REQUESTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID,
        Key.FNR
    )
    override val dataKeys = listOf(
        Key.ORGNRUNDERENHET,
        Key.TILGANG
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
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
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            val orgnrKey = RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET)

            if (isDataCollected(listOf(orgnrKey))) {
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
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
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
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

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

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val manglendeDatafelt = when (utloesendeBehov) {
            BehovType.HENT_IM_ORGNR -> Key.ORGNRUNDERENHET
            BehovType.TILGANGSKONTROLL -> Key.TILGANG
            else -> null
        }

        val feilReport = if (manglendeDatafelt != null) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, manglendeDatafelt)

            sikkerLogger.error("Returnerer feilmelding: '${feilmelding.melding}'")

            FeilReport(
                mutableListOf(feilmelding)
            )
        } else {
            FeilReport()
        }

        val clientId = RedisKey.of(fail.transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("$event forsøkt terminert, kunne ikke finne ${fail.transaksjonId} i redis!")
        } else {
            RedisKey.of(clientId).write(feilReport.toJson(FeilReport.serializer()))

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("$event terminert.")
            }
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)
}
