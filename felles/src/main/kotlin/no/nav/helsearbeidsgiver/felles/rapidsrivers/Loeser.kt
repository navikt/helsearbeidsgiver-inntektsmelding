package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail.Companion.publish
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class Loeser(val rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        configure(
            River(rapidsConnection).apply {
                validate(accept())
            }
        ).register(this)
    }

    abstract fun accept(): River.PacketValidation

    private fun configure(river: River): River {
        return river.validate {
            Behov.packetValidator.validate(it)
        }
    }

    // Var forsiktig å bruke det, hvis du kan.
    // Alle løser som publiserer Behov vil få kunskap om nedstrøms løserne.
    // i tilleg gjenbruktbarhet av løseren vil vare betydelig redusert
    fun publishBehov(behov: Behov) {
        behov.jsonMessage
            .toJson()
            .parseJson()
            .also { rapidsConnection.publish(it.toString()) }
            .also {
                logger.info("Publiserte behov for eventname ${behov.event} and uuid '${behov.uuid()}'.")
                sikkerLogger.info("Publiserte behov:\n${it.toPretty()}")
            }
    }

    fun publishEvent(event: Event) {
        event.jsonMessage
            .toJson()
            .parseJson()
            .also { rapidsConnection.publish(it.toString()) }
            .also {
                logger.info("Publiserte event for eventname ${event.event} and uuid ${event.jsonMessage[Key.UUID.str].asText()}'.")
                sikkerLogger.info("Publiserte event:\n${it.toPretty()}")
            }
    }

    fun publishData(data: Data) {
        data.jsonMessage
            .toJson()
            .parseJson()
            .also { rapidsConnection.publish(it.toString()) }
            .also {
                logger.info("Publiserte data for eventname ${data.event.name} and uuid ${data.jsonMessage[Key.UUID.str].asText()}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail)
            .also {
                logger.info("Publiserte feil for eventname '${fail.event.name}'.")
                sikkerLogger.info("Publiserte feil:\n${it.toPretty()}")
            }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding med behov '${packet[Key.BEHOV.str].asText()}'.")
        if (packet[Key.FORESPOERSEL_ID.str].asText().isEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }
        sikkerLogger.info("Mottok melding:\n${packet.toPretty()}")
        if (!packet[Key.BEHOV.str].isArray) {
            val behov = Behov(
                EventName.valueOf(packet[Key.EVENT_NAME.str].asText()),
                BehovType.valueOf(packet[Key.BEHOV.str].asText()),
                packet[Key.FORESPOERSEL_ID.str].asText(),
                packet
            )
            onBehov(behov)
        }
    }

    abstract fun onBehov(behov: Behov)
}
