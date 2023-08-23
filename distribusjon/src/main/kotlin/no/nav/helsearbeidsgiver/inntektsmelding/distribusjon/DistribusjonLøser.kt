@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.prometheus.client.Summary
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.JournalførtInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

private const val TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN = "helsearbeidsgiver.inntektsmelding"

class DistribusjonLøser(
    rapidsConnection: RapidsConnection,
    private val kafkaProducer: KafkaProducer<String, String>
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val requestLatency = Summary.build()
        .name("simba_distribusjon_inntektsmelding_latency_seconds")
        .help("distribusjon inntektsmelding latency in seconds")
        .register()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.DISTRIBUER_IM.name)
            it.requireKey(DataFelt.INNTEKTSMELDING_DOKUMENT.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    private fun hentInntektsmeldingDokument(packet: JsonMessage): InntektsmeldingDokument {
        try {
            return customObjectMapper().treeToValue(
                packet[DataFelt.INNTEKTSMELDING_DOKUMENT.str],
                InntektsmeldingDokument::class.java
            )
        } catch (ex: Exception) {
            throw DeserialiseringException(ex)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("Skal distribuere pakken:\n${packet.toPretty()}")
        val journalpostId: String = packet[Key.JOURNALPOST_ID.str].asText()
        logger.info("Skal distribuere inntektsmelding for journalpostId $journalpostId...")
        val eventName = packet[Key.EVENT_NAME.str].asText()
        val requestTimer = requestLatency.startTimer()
        try {
            val inntektsmeldingDokument = hentInntektsmeldingDokument(packet)
            val journalførtInntektsmelding = JournalførtInntektsmelding(inntektsmeldingDokument, journalpostId)
            val journalførtJson = customObjectMapper().writeValueAsString(journalførtInntektsmelding)
            kafkaProducer.send(ProducerRecord(TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN, journalførtJson))
            logger.info("Distribuerte eksternt for journalpostId: $journalpostId")
            sikkerLogger.info("Distribuerte eksternt for journalpostId: $journalpostId json: $journalførtJson")
            publishEvent(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_DISTRIBUERT,
                        DataFelt.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                        Key.JOURNALPOST_ID.str to journalpostId
                    )
                )
            )
            logger.info("Distribuerte inntektsmelding for journalpostId: $journalpostId")
        } catch (e: DeserialiseringException) {
            logger.error("Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId")
            sikkerLogger.error("Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId", e)
            publishFail(
                JsonMessage.newMessage(
                    eventName,
                    mapOf(
                        Key.FAIL.str to "Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId",
                        Key.JOURNALPOST_ID.str to journalpostId
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId")
            sikkerLogger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId", e)
            publishFail(
                JsonMessage.newMessage(
                    eventName,
                    mapOf(
                        Key.FAIL.str to "Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId",
                        Key.JOURNALPOST_ID.str to journalpostId
                    )
                )
            )
        } finally {
            requestTimer.observeDuration()
        }
    }
}
