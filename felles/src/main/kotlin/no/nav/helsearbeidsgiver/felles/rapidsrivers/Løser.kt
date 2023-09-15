package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class Løser(val rapidsConnection: RapidsConnection) : River.PacketListener {

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
        behov.toJsonMessage()
            .also {
                rapidsConnection.publish(it.toJson())
            }.also {
                logger.info("Publiserte behov for eventname ${behov.event} and uuid ${behov.uuid()}'.")
                sikkerLogger.info("Publiserte behov:\n${it.toPretty()}")
            }
    }

    fun publishEvent(event: Event) {
        event.toJsonMessage()
            .also { rapidsConnection.publish(it.toJson()) }
            .also {
                logger.info("Publiserte event for eventname ${event.event} and uuid ${event.uuid()}'.")
                sikkerLogger.info("Publiserte event:\n${it.toPretty()}")
            }
    }

    fun publishData(data: Data) {
        data.toJsonMessage()
            .also { rapidsConnection.publish(it.toJson()) }
            .also {
                logger.info("Publiserte data for eventname ${data.event.name} and uuid ${data.uuid()}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    fun publishFail(fail: Fail) {
        fail.toJsonMessage()
            .also { rapidsConnection.publish(it.toJson()) }
            .also {
                logger.info("Publiserte feil for eventname ${fail.event.name} and '${fail.behov?.name}'.")
                sikkerLogger.info("Publiserte feil:\n${it.toPretty()}")
            }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding med behov '${packet[Key.BEHOV.str].asText()}'.")
        sikkerLogger.info("Mottok melding:\n${packet.toPretty()}")
        if (!packet[Key.BEHOV.str].isArray) {
            val behov = Behov.create(packet)
            onBehov(behov)
        }
    }

    open fun onBehov(behov: Behov) {
    }
}
