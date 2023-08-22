@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

private const val TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN = "helsearbeidsgiver.inntektsmelding"

class DistribusjonLøser(
    rapidsConnection: RapidsConnection,
    private val kafkaProducer: KafkaProducer<String, String>
) : Løser(rapidsConnection) {

    private val logger = logger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.DISTRIBUER_IM.name)
            it.requireKey(DataFelt.INNTEKTSMELDING_DOKUMENT.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    private fun hentInntektsmeldingDokument(behov: Behov): InntektsmeldingDokument {
        try {
            return customObjectMapper().treeToValue(
                behov[DataFelt.INNTEKTSMELDING_DOKUMENT],
                InntektsmeldingDokument::class.java
            )
        } catch (ex: Exception) {
            throw DeserialiseringException(ex)
        }
    }

    override fun onBehov(behov: Behov) {
        val journalpostId: String = behov[Key.JOURNALPOST_ID].asText()
        logger.info("Skal distribuere inntektsmelding for journalpostId $journalpostId...")
        try {
            val inntektsmeldingDokument = hentInntektsmeldingDokument(behov)
            val journalførtInntektsmelding = JournalførtInntektsmelding(inntektsmeldingDokument, journalpostId)
            val journalførtJson = customObjectMapper().writeValueAsString(journalførtInntektsmelding)
            kafkaProducer.send(ProducerRecord(TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN, journalførtJson))
            logger.info("Distribuerte eksternt for journalpostId: $journalpostId")
            sikkerLogger.info("Distribuerte eksternt for journalpostId: $journalpostId json: $journalførtJson")

            Event.create(
                EventName.INNTEKTSMELDING_DISTRIBUERT,
                behov.forespoerselId!!,
                mapOf(
                    Key.JOURNALPOST_ID to journalpostId,
                    DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument
                )
            ).also {
                publishEvent(it)
            }

            logger.info("Distribuerte inntektsmelding for journalpostId: $journalpostId")
        } catch (e: DeserialiseringException) {
            logger.error("Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId")
            sikkerLogger.error("Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId", e)
            publishFail(
                behov.createFail(
                    "Distribusjon feilet fordi InntektsmeldingDokument ikke kunne leses for journalpostId: $journalpostId",
                    mapOf(Key.JOURNALPOST_ID to journalpostId)
                )
            )
        } catch (e: Exception) {
            logger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId")
            sikkerLogger.error("Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId", e)
            publishFail(
                behov.createFail(
                    "Klarte ikke distribuere inntektsmelding for journalpostId: $journalpostId",
                    mapOf(Key.JOURNALPOST_ID to journalpostId)
                )
            )
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }
}
