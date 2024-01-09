package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class JournalfoerInntektsmeldingMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    override val event: EventName = EventName.INNTEKTSMELDING_MOTTATT

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.requireKeys(
                Key.UUID,
                Key.INNTEKTSMELDING_DOKUMENT
            )
        }

    override fun onEvent(packet: JsonMessage) {
        val melding = packet.toJson().parseJson().toMap()
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.INNTEKTSMELDING_MOTTATT),
            Log.transaksjonId(transaksjonId)
        ) {
            "Mottok melding med event '${EventName.INNTEKTSMELDING_MOTTATT}'. Sender behov '${BehovType.JOURNALFOER}'.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }

            val jsonMessage = JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    Key.BEHOV.str to BehovType.JOURNALFOER,
                    Key.UUID.str to transaksjonId,
                    Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
                )
            )

            publishBehov(jsonMessage)
        }
    }
}
