package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
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
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class OpprettOppgaveService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.OPPGAVE_OPPRETT_REQUESTED
    override val startKeys = listOf(
        Key.ORGNRUNDERENHET,
        Key.FORESPOERSEL_ID,
        Key.UUID
    )
    override val dataKeys = listOf(
        Key.VIRKSOMHET
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { transaksjonId, forespoerselId ->
            val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson()
            )
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { _, _ ->
            "Service skal aldri være \"underveis\".".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            val orgnr = redisStore.get(RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET))
            if (orgnr == null) {
                "Mangler orgnr i redis. Klarer ikke opprette oppgave.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            val virksomhetNavn = redisStore.get(RedisKey.of(transaksjonId, Key.VIRKSOMHET))
                ?: defaultVirksomhetNavn()

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.OPPRETT_OPPGAVE.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.VIRKSOMHET to virksomhetNavn.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson()
            )
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            if (utloesendeBehov == BehovType.VIRKSOMHET) {
                val virksomhetKey = RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET)
                redisStore.set(virksomhetKey, defaultVirksomhetNavn())
                return finalize(melding)
            }

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            } else {
                redisStore.set(RedisKey.of(clientId), fail.feilmelding)
            }
        }
    }

    private inline fun medTransaksjonIdOgForespoerselId(melding: Map<Key, JsonElement>, block: (UUID, UUID) -> Unit) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED)
        ) {
            val transaksjonId = melding[Key.UUID]?.fromJson(UuidSerializer)
            if (transaksjonId == null) {
                "Mangler transaksjonId. Klarer ikke opprette oppgave.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            val forespoerselId = redisStore.get(RedisKey.of(transaksjonId, Key.FORESPOERSEL_ID))?.let(UUID::fromString)
                ?: melding[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

            if (forespoerselId == null) {
                MdcUtils.withLogFields(
                    Log.transaksjonId(transaksjonId)
                ) {
                    "Mangler forespoerselId. Klarer ikke opprette oppgave.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
                return
            }

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                block(transaksjonId, forespoerselId)
            }
        }
    }

    private fun defaultVirksomhetNavn(): String =
        "Arbeidsgiver"
}
