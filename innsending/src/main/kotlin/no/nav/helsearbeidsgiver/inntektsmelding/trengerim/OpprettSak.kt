package no.nav.helsearbeidsgiver.inntektsmelding.trengerim

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.CompositeEventListener
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.DataFelter
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.StatefullDataKanal
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.Transaction

class OpprettSak(val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    init {
        StatefullEventListener(redisStore, event, arrayOf(Key.ORGNRUNDERENHET.str, Key.IDENTITETSNUMMER.str), this, rapidsConnection)
        StatefullDataKanal(arrayOf(DataFelter.ARBEIDSTAKER_INFORMASJON.str), event, this, rapidsConnection, redisStore)
        DelegatingFailKanal(event, this, rapidsConnection)
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val forespørselId = message[Key.FORESPOERSEL_ID.str].asText()
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.BEHOV.str to listOf(BehovType.FULLT_NAVN.name),
                        Key.IDENTITETSNUMMER.str to redisStore.get(transaksjonsId + Key.IDENTITETSNUMMER.str)!!,
                        Key.FORESPOERSEL_ID.str to forespørselId
                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            if (isDataCollected(*arrayOf(Key.SAK_ID.str))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespørselId,
                            Key.SAK_ID.str to redisStore.get(transaksjonsId + Key.SAK_ID.str)!!
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*arrayOf(DataFelter.ARBEIDSTAKER_INFORMASJON.str))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
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
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                    Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                    Key.SAK_ID.str to redisStore.get(Key.SAK_ID.str)!!
                )
            ).toJson()
        )
    }

    override fun terminate(message: JsonMessage) {
        TODO("Not yet implemented")
    }
}
