package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class Loeser(
    val rapidsConnection: RapidsConnection,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapidsConnection)
            .apply {
                validate(accept())
                validate {
                    Behov.packetValidator.validate(it)
                }
            }.register(this)
    }

    abstract fun accept(): River.PacketValidation

    abstract fun onBehov(behov: Behov)

    fun publishFail(fail: Fail) {
        rapidsConnection
            .publish(fail.tilMelding())
            .also {
                logger.info("Publiserte feil for eventname '${fail.event.name}'.")
                sikkerLogger.info("Publiserte feil:\n${it.toPretty()}")
            }
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val melding = packet.toJson().parseJson().toMap()

        val eventName = Key.EVENT_NAME.les(EventName.serializer(), melding)
        val behovType = Key.BEHOV.les(BehovType.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.behov(behovType),
        ) {
            logger.info("Mottok melding med behov '$behovType'.")
            sikkerLogger.info("Mottok melding:\n${packet.toPretty()}")

            if (forespoerselId == null) {
                logger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er 'null'.")
                sikkerLogger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er 'null'.")
            } else if (forespoerselId.isEmpty()) {
                logger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er en tom streng.")
                sikkerLogger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er en tom streng.")
            }
        }

        val behov = Behov(eventName, behovType, forespoerselId, packet)

        onBehov(behov)
    }
}
