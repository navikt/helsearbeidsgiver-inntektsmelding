package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
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
    override val startKeys = setOf(
        Key.UUID,
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER
    )
    override val dataKeys = setOf(
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.SAK_ID,
        Key.PERSISTERT_SAK_ID
    )

    private val step2Keys = setOf(Key.ARBEIDSTAKER_INFORMASJON)
    private val step3Keys = setOf(Key.SAK_ID)

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        medTransaksjonIdOgForespoerselId(melding) { transaksjonId, forespoerselId ->
            val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)

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
            if (step3Keys.all(melding::containsKey)) {
                val sakId = Key.SAK_ID.les(String.serializer(), melding)

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.PERSISTER_SAK_ID.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SAK_ID to sakId.toJson()
                )
            } else if (step2Keys.all(melding::containsKey)) {
                val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
                val arbeidstaker = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)

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
                val sakId = Key.SAK_ID.les(String.serializer(), melding)

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
                val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                val ukjentPersonJson = PersonDato("Ukjent person", null, fnr).toJson(PersonDato.serializer())

                redisStore.set(RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON), ukjentPersonJson.toString())

                val meldingMedDefault = mapOf(Key.ARBEIDSTAKER_INFORMASJON to ukjentPersonJson).plus(melding)

                return inProgress(meldingMedDefault)
            }

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                "Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
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
            val transaksjonId = Key.UUID.les(UuidSerializer, melding)
            val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                block(transaksjonId, forespoerselId)
            }
        }
    }
}
