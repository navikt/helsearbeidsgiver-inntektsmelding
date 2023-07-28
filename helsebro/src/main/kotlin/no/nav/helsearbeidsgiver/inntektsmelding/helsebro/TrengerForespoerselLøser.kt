package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import java.util.UUID

class TrengerForespoerselLøser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer<TrengerForespoersel>
) : Løser(rapid) {

    private val logger = logger()

    init {
        sikkerLogger.info("Starting TrengerForespoerselLøser...")
    }

    override fun accept(): River.PacketValidation =
        River.PacketValidation { msg ->
            msg.demandValues(
                Key.BEHOV to BehovType.HENT_TRENGER_IM.name
            )
            msg.require(
                DataFelt.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) },
                Key.UUID to { it.fromJson(UuidSerializer) }
            )
        }

    override fun onBehov(packet: JsonMessage) {
        val json = packet.toJsonMap()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.HENT_TRENGER_IM)
        ) {
            logger.info("Mottok behov om ${BehovType.HENT_TRENGER_IM}.")
            sikkerLogger.info("Mottok behov:\n${packet.toPretty()}")

            val event = Key.EVENT_NAME.les(EventName.serializer(), json)
            val transaksjonId = Key.UUID.les(UuidSerializer, json)
            val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json)

            MdcUtils.withLogFields(
                Log.event(event),
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                spoerrEtterForespoersel(event, transaksjonId, forespoerselId)
            }
        }
    }

    private fun spoerrEtterForespoersel(event: EventName, transaksjonId: UUID, forespoerselId: UUID) {
        val trengerForespoersel = TrengerForespoersel(
            forespoerselId = forespoerselId,
            boomerang = mapOf(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson()
            ).toJson()
        )

        priProducer.send(trengerForespoersel)
            .ifTrue {
                logger.info("Publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
                sikkerLogger.info("Publiserte melding på pri-topic:\n${trengerForespoersel.toJson(TrengerForespoersel.serializer()).toPretty()}")
            }
            .ifFalse {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
            }
    }
}

private fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer()
        )
    )
