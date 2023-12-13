package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class OpprettOppgaveService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.OPPGAVE_OPPRETT_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(Key.ORGNRUNDERENHET, Key.FORESPOERSEL_ID, Key.UUID),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(Key.VIRKSOMHET),
                event,
                this,
                rapidsConnection,
                redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED)
        ) {
            val json = message.toJson().parseJson().toMap()

            val transaksjonsId = json[Key.UUID]?.fromJson(UuidSerializer)
            if (transaksjonsId == null) {
                "Mangler transaksjonId. Klarer ikke opprette oppgave.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))?.let(UUID::fromString)
                ?: json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

            if (forespoerselId == null) {
                MdcUtils.withLogFields(
                    Log.transaksjonId(transaksjonsId)
                ) {
                    "Mangler forespoerselId. Klarer ikke opprette oppgave.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
                return
            }

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonsId),
                Log.forespoerselId(forespoerselId)
            ) {
                if (transaction == Transaction.NEW) {
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.UUID.str to transaksjonsId,
                                Key.BEHOV.str to BehovType.VIRKSOMHET.name,
                                Key.FORESPOERSEL_ID.str to forespoerselId,
                                Key.ORGNRUNDERENHET.str to message[Key.ORGNRUNDERENHET.str]
                            )
                        ).toJson()
                    )
                }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED),
            Log.transaksjonId(transaksjonsId),
            Log.forespoerselId(forespoerselId)
        ) {
            val orgnr = redisStore.get(RedisKey.of(transaksjonsId, Key.ORGNRUNDERENHET))
            if (orgnr == null) {
                "Mangler orgnr i redis. Klarer ikke opprette oppgave.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            val virksomhetNavn = redisStore.get(RedisKey.of(transaksjonsId, Key.VIRKSOMHET))
                ?: defaultVirksomhetNavn()

            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.BEHOV.str to BehovType.OPPRETT_OPPGAVE,
                        Key.UUID.str to transaksjonsId,
                        Key.FORESPOERSEL_ID.str to forespoerselId,
                        Key.VIRKSOMHET.str to virksomhetNavn,
                        Key.ORGNRUNDERENHET.str to orgnr
                    )
                ).toJson()
            )
        }
    }

    override fun terminate(fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.OPPGAVE_OPPRETT_REQUESTED),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            } else {
                redisStore.set(RedisKey.of(clientId), fail.feilmelding)
            }
        }
    }

    override fun onError(feil: Fail): Transaction {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), feil.utloesendeMelding.toMap())
        if (utloesendeBehov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(feil.transaksjonId, Key.VIRKSOMHET)
            redisStore.set(virksomhetKey, defaultVirksomhetNavn())
            return Transaction.FINALIZE
        }
        return Transaction.TERMINATE
    }

    private fun defaultVirksomhetNavn(): String =
        "Arbeidsgiver"
}
