package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.prometheus.client.Counter
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er besvart. */
sealed class ForespoerselBesvartLoeser : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val forespoerselBesvartCounter: Counter

    abstract fun Map<IKey, JsonElement>.lesMelding(): Melding

    abstract fun haandterFeil(json: JsonElement)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this)
        ) {
            runCatching {
                opprettEvent(json, context)
            }
                .onFailure { e ->
                    "Ukjent feil.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)
                    }

                    haandterFeil(json)
                }
        }
    }

    private fun opprettEvent(json: JsonElement, context: MessageContext) {
        val melding = json.toMap().lesMelding()

        logger.info("Mottok melding om '${melding.event}'.")

        MdcUtils.withLogFields(
            Log.event(EventName.FORESPOERSEL_BESVART),
            Log.behov(BehovType.NOTIFIKASJON_HENT_ID),
            Log.forespoerselId(melding.forespoerselId),
            Log.transaksjonId(melding.transaksjonId),
            bestemLoggFelt(melding.event)
        ) {
            context.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
                Key.FORESPOERSEL_ID to melding.forespoerselId.toJson(),
                Key.TRANSACTION_ORIGIN to melding.transaksjonId.toJson()
            )
                .also {
                    logger.info("Publiserte melding. Se sikkerlogg for mer info.")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    forespoerselBesvartCounter.inc()
                }
        }
    }
}

data class Melding(
    val event: String,
    val forespoerselId: UUID,
    val transaksjonId: UUID
)

private fun bestemLoggFelt(event: String): Pair<String, String> {
    val priNotis = Pri.NotisType.entries.firstOrNull { it.toString() == event }
    val eventName = EventName.entries.firstOrNull { it.toString() == event }

    return when {
        priNotis != null -> Log.priNotis(priNotis)
        eventName != null -> Log.event(eventName)
        else -> Log.ukjentType(event)
    }
}
