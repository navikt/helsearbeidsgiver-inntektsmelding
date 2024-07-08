package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class InntektService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : CompositeEventListener() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.INNTEKT_REQUESTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
            Key.SKJAERINGSTIDSPUNKT,
        )
    override val dataKeys =
        setOf(
            Key.FORESPOERSEL_SVAR,
            Key.INNTEKT,
        )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
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
            Log.forespoerselId(forespoerselId),
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )
                .also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.HENT_TRENGER_IM),
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
            Log.forespoerselId(forespoerselId),
        ) {
            if (Key.FORESPOERSEL_SVAR in melding) {
                val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
                val skjaeringstidspunkt = Key.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, melding)

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.INNTEKT.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.FNR to forespoersel.fnr.toJson(),
                    Key.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.INNTEKT),
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

        val clientId =
            RedisKey.of(transaksjonId, event)
                .read()
                ?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
            logger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
        } else {
            val inntekt = Key.INNTEKT.les(Inntekt.serializer(), melding)

            val resultJson =
                ResultJson(
                    success = inntekt.toJson(Inntekt.serializer()),
                )
                    .toJson(ResultJson.serializer())

            RedisKey.of(clientId).write(resultJson)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(transaksjonId),
            ) {
                sikkerLogger.info("$event fullført.")
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val clientId =
            RedisKey.of(fail.transaksjonId, event)
                .read()
                ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId),
            ) {
                sikkerLogger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.transaksjonId} i Redis")
                logger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.transaksjonId} i Redis")
            }
        } else {
            val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
            val resultJson =
                ResultJson(
                    failure = feilmelding.toJson(),
                )
                    .toJson(ResultJson.serializer())

            "Returnerer feilmelding: '$feilmelding'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            RedisKey.of(clientId).write(resultJson)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(fail.transaksjonId),
            ) {
                sikkerLogger.error("$event terminert.")
            }
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? = redisStore.get(this)
}
