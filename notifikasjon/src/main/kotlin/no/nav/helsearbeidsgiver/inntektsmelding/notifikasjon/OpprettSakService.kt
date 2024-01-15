package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
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
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class OpprettSakService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.SAK_OPPRETT_REQUESTED
    override val startKeys = listOf(
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER,
        Key.FORESPOERSEL_ID,
        Key.UUID
    )
    override val dataKeys = listOf(
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.SAK_ID,
        Key.PERSISTERT_SAK_ID
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { transaksjonId, forespoerselId ->
            val fnr = redisStore.get(RedisKey.of(transaksjonId, Key.IDENTITETSNUMMER))
            if (fnr == null) {
                "Mangler fnr i redis. Klarer ikke opprette sak.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.IDENTITETSNUMMER to fnr.toJson()
            )
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { transaksjonId, forespoerselId ->
            if (isDataCollected(steg3(transaksjonId))) {
                val sakId = redisStore.get(RedisKey.of(transaksjonId, Key.SAK_ID))
                if (sakId == null) {
                    "Mangler sakId i redis. Klarer ikke opprette sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    return
                }

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.PERSISTER_SAK_ID.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SAK_ID to sakId.toJson()
                )
            } else if (isDataCollected(steg2(transaksjonId))) {
                val orgnr = redisStore.get(RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET))
                if (orgnr == null) {
                    "Mangler orgnr i redis. Klarer ikke opprette sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    return
                }

                val arbeidstaker = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))
                    ?.fromJson(PersonDato.serializer())
                    ?: ukjentArbeidstaker()

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.ARBEIDSTAKER_INFORMASJON to arbeidstaker.toJson(PersonDato.serializer())
                )
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { transaksjonId, forespoerselId ->
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(EventName.SAK_OPPRETT_REQUESTED),
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                val sakId = redisStore.get(RedisKey.of(transaksjonId, Key.SAK_ID))
                if (sakId == null) {
                    "Mangler sakId i redis. Klarer ikke publisere event om opprettet sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    return
                }

                rapid.publish(
                    Key.EVENT_NAME to EventName.SAK_OPPRETTET.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SAK_ID to sakId.toJson()
                )
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.SAK_OPPRETT_REQUESTED),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            if (utloesendeBehov == BehovType.FULLT_NAVN) {
                val arbeidstakerKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
                redisStore.set(arbeidstakerKey, ukjentArbeidstaker().toJsonStr(PersonDato.serializer()))
                return inProgress(melding)
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
            Log.event(EventName.SAK_OPPRETT_REQUESTED)
        ) {
            val transaksjonId = melding[Key.UUID]?.fromJson(UuidSerializer)
            if (transaksjonId == null) {
                "Mangler transaksjonId. Klarer ikke opprette sak.".also {
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
                    "Mangler forespoerselId. Klarer ikke opprette sak.".also {
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

    private fun ukjentArbeidstaker(): PersonDato =
        PersonDato("Ukjent person", null, "")

    private fun steg2(transactionId: UUID): Set<RedisKey> = setOf(RedisKey.of(transactionId, Key.ARBEIDSTAKER_INFORMASJON))
    private fun steg3(transactionId: UUID): Set<RedisKey> = setOf(RedisKey.of(transactionId, Key.SAK_ID))
}
