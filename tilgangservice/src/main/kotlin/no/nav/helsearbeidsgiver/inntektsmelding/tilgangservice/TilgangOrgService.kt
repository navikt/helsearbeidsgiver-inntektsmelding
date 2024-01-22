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

class TilgangOrgService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.TILGANG_ORG_REQUESTED
    override val startKeys = listOf(
        Key.ORGNRUNDERENHET,
        Key.FNR
    )
    override val dataKeys = listOf(
        Key.TILGANG
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val fnr = Key.FNR.les(String.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(),
                Key.FNR to fnr.toJson()
            )
                .also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.TILGANGSKONTROLL)
                    ) {
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                    }
                }
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        "Service skal aldri være \"underveis\".".also {
            logger.error(it)
            sikkerLogger.error(it)
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
                val tilgang = RedisKey.of(transaksjonId, Key.TILGANG).read()
                val feil = RedisKey.of(transaksjonId, Feilmelding("")).read()

                val tilgangJson = TilgangData(
                    tilgang = tilgang?.fromJson(Tilgang.serializer()),
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

        val feilReport = if (utloesendeBehov == BehovType.TILGANGSKONTROLL) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, Key.TILGANG)

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
