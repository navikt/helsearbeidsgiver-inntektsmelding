package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class DistribuerIMLøser(private val rapidsConnection: RapidsConnection, val kafkaProducer: KafkaProducer<String, String>) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_JOURNALFØRT.name)
                it.demandValue(Key.BEHOV.str,BehovType.DISTRIBUER_IM.name)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.requireKey(Key.JOURNALPOST_ID.str)
            }
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Skal distribuere pakke")
        sikkerlogg.info("Skal distribuere pakken: ${packet.toJson()}")
        try {
            val inntektsmeldingDokument: InntektsmeldingDokument = packet[Key.INNTEKTSMELDING_DOKUMENT.str].toJsonElement().fromJson()
            val journalpostId: String = packet[Key.JOURNALPOST_ID.str].asText()
            val packet: JsonMessage = JsonMessage.newMessage(
                mapOf(
                    Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                    Key.JOURNALPOST_ID.str to journalpostId
                )
            )
            kafkaProducer.send(
                ProducerRecord(
                    "inntektsmelding",
                    packet.toString()
                )
            ).get()
            sikkerlogg.info("Publisert eksternt for journalpostId: $journalpostId.")
            val packet2: JsonMessage = JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_DISTRIBUERT,
                    Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                    Key.JOURNALPOST_ID.str to journalpostId
                )
            )
            rapidsConnection.publish(packet2.toJson())
            sikkerlogg.info("Publisert internt for journalpostId: $journalpostId")
        } catch (e: Exception) {
            logger.error("Klarte ikke lese ut inntektsmeldingdokument")
            sikkerlogg.error("Klarte ikke lese ut inntektsmeldingdokument", e)
        }
    }
}
