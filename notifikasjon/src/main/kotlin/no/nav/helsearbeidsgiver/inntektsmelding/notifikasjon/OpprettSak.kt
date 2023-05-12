package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.StatefullDataKanal
import java.util.UUID

class OpprettSak(val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(Key.ORGNRUNDERENHET.str, Key.IDENTITETSNUMMER.str, Key.FORESPOERSEL_ID.str),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(DataFelt.ARBEIDSTAKER_INFORMASJON.str, DataFelt.SAK_ID.str, DataFelt.PERSISTERT_SAK_ID.str),
                event,
                this,
                rapidsConnection,
                redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val forespørselId = redisStore.get(transaksjonsId + Key.FORESPOERSEL_ID.str)!!
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.UUID.str to transaksjonsId,
                        Key.BEHOV.str to listOf(BehovType.FULLT_NAVN.name),
                        Key.IDENTITETSNUMMER.str to redisStore.get(transaksjonsId + Key.IDENTITETSNUMMER.str)!!,
                        Key.FORESPOERSEL_ID.str to forespørselId

                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            if (isDataCollected(*steg3(transaksjonsId))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespørselId,
                            DataFelt.SAK_ID.str to redisStore.get(transaksjonsId + DataFelt.SAK_ID.str)!!
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*steg2(transaksjonsId))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.OPPRETT_SAK,
                            Key.FORESPOERSEL_ID.str to forespørselId,
                            Key.ORGNRUNDERENHET.str to redisStore.get(transaksjonsId + Key.ORGNRUNDERENHET.str)!!
                        )
                    ).toJson()
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                    Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                    DataFelt.SAK_ID.str to redisStore.get(transaksjonsId + DataFelt.SAK_ID.str)!!
                )
            ).toJson()
        )
    }

    override fun terminate(message: JsonMessage) {
        TODO("Not yet implemented")
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.FULLT_NAVN) {
            val fulltNavnKey = "${feil.uuid}${DataFelt.ARBEIDSTAKER_INFORMASJON.str}"
            redisStore.set(fulltNavnKey, customObjectMapper().writeValueAsString(PersonDato("Ukjent person", null)))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    fun steg2(transactionId: String) = arrayOf(transactionId + DataFelt.ARBEIDSTAKER_INFORMASJON.str)
    fun steg3(transactionId: String) = arrayOf(transactionId + DataFelt.SAK_ID.str)
}
