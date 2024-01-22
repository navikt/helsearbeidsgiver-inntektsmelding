package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ManuellOpprettSakService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val sikkerLogger = sikkerLogger()

    override val event = EventName.MANUELL_OPPRETT_SAK_REQUESTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID,
        Key.UUID
    )
    override val dataKeys = listOf(
        Key.FORESPOERSEL_SVAR,
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
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        val forespoerselId = redisStore.get(RedisKey.of(transaksjonId, Key.FORESPOERSEL_ID))!!

        val forespoersel = redisStore.get(RedisKey.of(transaksjonId, Key.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())
        if (forespoersel == null) {
            sikkerLogger.error("Fant ikke forespørsel '$forespoerselId' i redis-cache. transaksjonId='$transaksjonId'")
            return
        }

        when {
            isDataCollected(steg4(transaksjonId)) -> {
                val sakId = redisStore.get(RedisKey.of(transaksjonId, Key.SAK_ID))!!

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.PERSISTER_SAK_ID.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SAK_ID to sakId.toJson()
                )

                if (forespoersel.erBesvart) {
                    rapid.publish(
                        Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.SAK_ID to sakId.toJson()
                    )
                }
            }

            isDataCollected(steg3(transaksjonId)) -> {
                val arbeidstaker = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))!!.fromJson(PersonDato.serializer())

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.ARBEIDSTAKER_INFORMASJON to arbeidstaker.toJson(PersonDato.serializer())
                )
            }

            isDataCollected(steg2(transaksjonId)) -> {
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.IDENTITETSNUMMER to forespoersel.fnr.toJson()
                )
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        val sakId = redisStore.get(RedisKey.of(transaksjonId, Key.SAK_ID))!!

        rapid.publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETTET.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.SAK_ID to sakId.toJson()
        )
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        sikkerLogger.error("Mottok feil:\n$fail")
    }

    private fun steg2(transactionId: UUID): Set<RedisKey> = setOf(RedisKey.of(transactionId, Key.FORESPOERSEL_SVAR))
    private fun steg3(transactionId: UUID): Set<RedisKey> = setOf(RedisKey.of(transactionId, Key.ARBEIDSTAKER_INFORMASJON))
    private fun steg4(transactionId: UUID): Set<RedisKey> = setOf(RedisKey.of(transactionId, Key.SAK_ID))
}
