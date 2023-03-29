package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import org.slf4j.LoggerFactory

class NotifikasjonInntektsmeldingMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    private val sikkerLogger = LoggerFactory.getLogger("tjenestekall")

    override val event: EventName = EventName.INNTEKTSMELDING_MOTTATT

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        logger.info("NotifikasjonInntektsmeldingMottattListener skal behandle inntektsmeldingdokument...")
        val inntektsmeldingDokument: InntektsmeldingDokument = customObjectMapper().treeToValue(
            packet[Key.INNTEKTSMELDING_DOKUMENT.str],
            InntektsmeldingDokument::class.java
        )
        sikkerLogger.info("NotifikasjonInntektsmeldingMottattListener fikk event ${EventName.INNTEKTSMELDING_MOTTATT}, pakke: ${packet.toJson()}")
        publishBehov(
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.NOTIFIKASJON_IM_MOTTATT.name,
                    Key.UUID.str to packet[Key.UUID.str],
                    Key.IDENTITETSNUMMER.str to inntektsmeldingDokument.identitetsnummer,
                    Key.ORGNRUNDERENHET.str to inntektsmeldingDokument.orgnrUnderenhet
                )
            )
        )
        logger.info("NotifikasjonInntektsmeldingMottattListener ferdigbehandlet inntektsmeldingdokument.")
    }
}
