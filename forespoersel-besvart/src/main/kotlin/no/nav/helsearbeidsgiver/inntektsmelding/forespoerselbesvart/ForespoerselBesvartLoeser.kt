package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

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

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er besvart. */
class ForespoerselBesvartLoeser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer<JsonElement>
) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.name
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
            "class" to this::class.simpleName.orEmpty(),
            "transaksjon_id" to transaksjonId.toString()
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

        context.publish(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.TRANSACTION_ORIGIN to transaksjonId.toJson()
        )
            .also {
                logger.info("Publiserte melding. Se sikkerlogg for mer info.")
                sikkerLogger.info("Publiserte melding:\n${toPretty()}")
            }
    }

    private fun JsonElement.republiser() {
        priProducer.send(this)
    }
}
