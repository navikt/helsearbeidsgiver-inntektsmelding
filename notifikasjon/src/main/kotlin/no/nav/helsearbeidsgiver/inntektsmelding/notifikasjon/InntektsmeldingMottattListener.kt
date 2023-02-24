package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

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

class InntektsmeldingMottattListener(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
                it.rejectKey(Key.BEHOV.str)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.interestedIn(Key.UUID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val inntektsmeldingDokument: InntektsmeldingDokument = packet[Key.INNTEKTSMELDING_DOKUMENT.str].toJsonElement().fromJson()
       logger.info("Inntektmelding mottat listener for bruker notification ${packet.toJson()}")
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    Key.BEHOV.str to BehovType.NOTIFIKASJON_IM_MOTTATT.name,
                    Key.UUID.str to packet[Key.UUID.str],
                    Key.IDENTITETSNUMMER.str to inntektsmeldingDokument.identitetsnummer,
                    Key.ORGNRUNDERENHET.str to inntektsmeldingDokument.orgnrUnderenhet
                )
            ).toJson()
        )
    }
}
