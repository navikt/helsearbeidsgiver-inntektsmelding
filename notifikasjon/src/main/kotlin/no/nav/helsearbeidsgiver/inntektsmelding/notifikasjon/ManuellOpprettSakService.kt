package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
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

    override fun new(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)

        // TODO Les fra melding
        val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))!!

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.UUID to transaksjonsId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )
    }

    override fun inProgress(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))!!
        val forespoersel = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())

        if (forespoersel == null) {
            sikkerLogger.error("Fant ikke forespørsel '$forespoerselId' i redis-cache. transaksjonId='$transaksjonsId'")
            return
        }

        when {
            isDataCollected(steg4(transaksjonsId)) -> {
                rapid.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))!!
                        )
                    ).toJson()
                )

                if (forespoersel.erBesvart) {
                    rapid.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to EventName.FORESPOERSEL_BESVART,
                                Key.UUID.str to transaksjonsId,
                                Key.FORESPOERSEL_ID.str to forespoerselId,
                                Key.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))!!
                            )
                        ).toJson()
                    )
                }
            }

            isDataCollected(steg3(transaksjonsId)) -> {
                val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonsId, Key.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer())
                rapid.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.OPPRETT_SAK,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.ORGNRUNDERENHET.str to forespoersel.orgnr,
                            // @TODO this transformation is not nessesary. StatefullDataKanal should be fixed to use Tree
                            Key.ARBEIDSTAKER_INFORMASJON.str to arbeidstakerRedis!!
                        )
                    ).toJson()
                )
            }

            isDataCollected(steg2(transaksjonsId)) -> {
                rapid.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                            Key.IDENTITETSNUMMER.str to forespoersel.fnr,
                            Key.FORESPOERSEL_ID.str to forespoerselId

                        )
                    ).toJson()
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        rapid.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                    Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                    Key.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))!!
                )
            ).toJson()
        )
    }

    override fun onError(message: JsonMessage, fail: Fail) {
        sikkerLogger.error("Mottok feil:\n$fail")
    }

    private fun steg2(transactionId: UUID): List<RedisKey> = listOf(RedisKey.of(transactionId, Key.FORESPOERSEL_SVAR))
    private fun steg3(transactionId: UUID): List<RedisKey> = listOf(RedisKey.of(transactionId, Key.ARBEIDSTAKER_INFORMASJON))
    private fun steg4(transactionId: UUID): List<RedisKey> = listOf(RedisKey.of(transactionId, Key.SAK_ID))
}
