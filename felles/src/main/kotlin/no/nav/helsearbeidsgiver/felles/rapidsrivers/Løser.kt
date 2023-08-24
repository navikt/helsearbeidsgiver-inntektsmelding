package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class Løser(val rapidsConnection: RapidsConnection) : River.PacketListener {
    lateinit var eventName: EventName
    lateinit var forespoerselId: String

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

    // Ungå å bruke det, hvis du kan.
    // Alle løser som publiserer Behov vil få kunskap om nedstrøms løserne.
    // i tilleg gjenbruktbarhet av løseren vil vare betydelig redusert
    fun publishBehov(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        if (forespoerselId.isNotEmpty()) {
            message[Key.FORESPOERSEL_ID.str] = forespoerselId
        }
        rapidsConnection.publish(message.toJson())
    }

    fun publishBehov(behov: Behov) {
        behov.toJsonMessage()
            .also {
                rapidsConnection.publish(it.toJson())
            }.also {
                logger.info("Publiserte behov for eventname ${behov.event} and uuid ${behov.uuid()}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    fun publishEvent(message: JsonMessage) {
        if (forespoerselId.isNotEmpty()) {
            message[Key.FORESPOERSEL_ID.str] = forespoerselId
        }
        rapidsConnection.publish(message.toJson())
    }

    fun publishEvent(event: Event) {
        event.toJsonMessage()
            .also { rapidsConnection.publish(it.toJson()) }
            .also {
                logger.info("Publiserte data for eventname ${event.event} and uuid ${event.uuid()}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
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

    fun publishData(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        rapidsConnection.publish(message.toJson())
    }

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail.toJsonMessage().toJson())
    }

    fun publishFail(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        rapidsConnection.publish(message.toJson())
    }
    fun publishFail(fail: no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail) {
        fail.toJsonMessage()
            .also { rapidsConnection.publish(it.toJson()) }
            .also {
                logger.info("Publiserte feil for eventname ${fail.event.name} and'${fail.behov?.name}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        eventName = EventName.valueOf(packet[Key.EVENT_NAME.str].asText())
        forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Mottok melding med behov '${packet[Key.BEHOV.str].asText()}'.")
        sikkerLogger.info("Mottok melding:\n${packet.toPretty()}")
        onBehov(packet)
        if (!packet[Key.BEHOV.str].isArray) {
            val behov = Behov.create(packet)
            onBehov(behov)
        }
    }

    abstract fun onBehov(packet: JsonMessage)

    open fun onBehov(behov: Behov) {
    }
}
