package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
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
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TilgangForespoerselService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val sikkerLogger = sikkerLogger()

    override val event = EventName.TILGANG_FORESPOERSEL_REQUESTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.FNR
    )
    override val dataKeys = setOf(
        Key.FORESPOERSEL_SVAR,
        Key.TILGANG
    )

    init {
        StatefullEventListener(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson()
            )
                .also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.HENT_TRENGER_IM)
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
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            if (Key.FORESPOERSEL_SVAR in melding) {
                val forespoersel = Key.FORESPOERSEL_SVAR.les(TrengerInntekt.serializer(), melding)
                val avsenderFnr = Key.FNR.les(String.serializer(), melding)

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.FNR to avsenderFnr.toJson()
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
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.clientId(clientId),
                Log.transaksjonId(transaksjonId)
            ) {
                val tilgang = Key.TILGANG.les(Tilgang.serializer(), melding)

                val feil = RedisKey.of(transaksjonId, Feilmelding("")).read()

                val tilgangJson = TilgangData(
                    tilgang = tilgang,
                    feil = feil?.fromJson(FeilReport.serializer())
                )
                    .toJson(TilgangData.serializer())

                RedisKey.of(clientId).write(tilgangJson)
                sikkerLogger.info("$event fullført.")
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val manglendeDatafelt = when (utloesendeBehov) {
            BehovType.HENT_TRENGER_IM -> Key.FORESPOERSEL_SVAR
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
            val tilgangJson = TilgangData(
                feil = feilReport
            )
                .toJson(TilgangData.serializer())

            RedisKey.of(clientId).write(tilgangJson)

            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
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
