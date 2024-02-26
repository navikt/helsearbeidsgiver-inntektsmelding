@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.prometheus.client.Summary
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishEvent
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

// TODO slett etter overgangsfase
class DistribusjonLoeser(
    rapidsConnection: RapidsConnection,
    private val kafkaProducer: KafkaProducer<String, String>
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val requestLatency = Summary.build()
        .name("simba_distribusjon_inntektsmelding_latency_seconds")
        .help("distribusjon inntektsmelding latency in seconds")
        .register()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.DISTRIBUER_IM.name)
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    private fun hentInntektsmeldingDokument(behov: Behov): Inntektsmelding {
        try {
            val json = behov[Key.INNTEKTSMELDING_DOKUMENT].toString()
            return json.fromJson(Inntektsmelding.serializer())
        } catch (ex: Exception) {
            throw DeserialiseringException(ex)
        }
    }

    override fun onBehov(behov: Behov) {
        val journalpostId: String = behov[Key.JOURNALPOST_ID].asText()
        logger.info("Skal distribuere inntektsmelding for journalpostId $journalpostId...")
        val requestTimer = requestLatency.startTimer()
        try {
            val inntektsmelding = hentInntektsmeldingDokument(behov)
            val journalfoertJson = JournalfoertInntektsmelding(journalpostId, inntektsmelding).toJsonStr(JournalfoertInntektsmelding.serializer())
            kafkaProducer.send(ProducerRecord(TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN, journalfoertJson))
            logger.info("Distribuerte eksternt for journalpostId: $journalpostId")
            sikkerLogger.info("Distribuerte eksternt for journalpostId: $journalpostId json: $journalfoertJson")

            rapidsConnection.publishEvent(
                eventName = EventName.INNTEKTSMELDING_DISTRIBUERT,
                transaksjonId = null,
                forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                Key.JOURNALPOST_ID to journalpostId.toJson(),
                Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer())
            )

            logger.info("Distribuerte inntektsmelding for journalpostId: $journalpostId")
        } catch (e: DeserialiseringException) {
            logger.error("Distribusjon feilet fordi Inntektsmelding ikke kunne leses for journalpostId: $journalpostId")
            sikkerLogger.error("Distribusjon feilet fordi Inntektsmelding ikke kunne leses for journalpostId: $journalpostId", e)
            publishFail(
                behov.createFail(
                    "Distribusjon feilet fordi Inntektsmelding ikke kunne leses for journalpostId: $journalpostId"
                )
            )
        } catch (e: Exception) {
            logger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId")
            sikkerLogger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId", e)
            publishFail(
                behov.createFail(
                    "Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId"
                )
            )
        } finally {
            requestTimer.observeDuration()
        }
    }
}
