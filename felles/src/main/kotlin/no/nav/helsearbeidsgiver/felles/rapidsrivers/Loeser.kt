package no.nav.helsearbeidsgiver.felles.rapidsrivers

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail.Companion.publish
import no.nav.helsearbeidsgiver.utils.json.parseJson
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

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail)
            .also {
                logger.info("Publiserte feil for eventname '${fail.event.name}'.")
                sikkerLogger.info("Publiserte feil:\n${it.toPretty()}")
            }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson().toMap()

        logger.info("Mottok melding med behov '${json[Key.BEHOV]}'.")
        sikkerLogger.info("Mottok melding:\n${packet.toPretty()}")

        val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), json)

        if (forespoerselId == null) {
            logger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er 'null'.")
            sikkerLogger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er 'null'.")
        } else if (forespoerselId.isEmpty()) {
            logger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er en tom streng.")
            sikkerLogger.warn("Mangler forespørselId! '${Key.FORESPOERSEL_ID}' er en tom streng.")
        }

        if (!packet[Key.BEHOV.str].isArray) {
            val behov = Behov(
                Key.EVENT_NAME.les(EventName.serializer(), json),
                Key.BEHOV.les(BehovType.serializer(), json),
                forespoerselId?.takeIf { it.isNotEmpty() },
                packet
            )
            onBehov(behov)
        }
    }

    abstract fun onBehov(behov: Behov)
}
