package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselforkastet

import io.prometheus.client.Counter
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er forkastet. */
class ForespoerselForkastetLoeser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer<JsonElement>
) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    private val forespoerselForkastetCounter = Counter.build()
        .name("simba_forespoersel_forkastet_total")
        .help("Antall foresporsler forkastet fra Spleis (pri-topic)")
        .register()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.name
                )
                it.requireKeys(
                    Pri.Key.FORESPOERSEL_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        val transaksjonId = randomUuid()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.priNotis(Pri.NotisType.FORESPOERSEL_FORKASTET),
            Log.transaksjonId(transaksjonId)
        ) {
            runCatching {
                json.opprettEvent(transaksjonId, context)
            }
                .onFailure { e ->
                    "Ukjent feil.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)
                    }

                    json.republiser()
                }
        }
    }

    private fun JsonElement.opprettEvent(transaksjonId: UUID, context: MessageContext) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        logger.info("Mottok melding på pri-topic om '${Pri.NotisType.FORESPOERSEL_BESVART}'.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${toPretty()}")

        val forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.event(EventName.FORESPOERSEL_BESVART),
            Log.behov(BehovType.NOTIFIKASJON_HENT_ID),
            Log.forespoerselId(forespoerselId)
        ) {
            context.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.toJson(),
                Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.TRANSACTION_ORIGIN to transaksjonId.toJson()
            )
                .also {
                    logger.info("Publiserte melding. Se sikkerlogg for mer info.")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    forespoerselForkastetCounter.inc()
                }
        }
    }

    private fun JsonElement.republiser() {
        priProducer.send(this)
    }
}
