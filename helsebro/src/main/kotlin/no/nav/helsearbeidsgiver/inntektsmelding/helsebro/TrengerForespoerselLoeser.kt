package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TrengerForespoerselLoeser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer
) : Loeser(rapid) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

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

    override fun onBehov(behov: Behov) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.HENT_TRENGER_IM)
        ) {
            logger.info("Mottok behov om ${BehovType.HENT_TRENGER_IM}.")

            val transaksjonId = behov.uuid().let { UUID.fromString(it) }
            val forespoerselId = behov.forespoerselId.let { UUID.fromString(it) }

            MdcUtils.withLogFields(
                Log.event(behov.event),
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                spoerrEtterForespoersel(behov.event, transaksjonId, forespoerselId)
            }
        }
    }

    private fun spoerrEtterForespoersel(event: EventName, transaksjonId: UUID, forespoerselId: UUID) {
        priProducer.send(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Pri.Key.BOOMERANG to mapOf(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson()
            ).toJson()
        )
            .onSuccess {
                logger.info("Publiserte melding på pri-topic om ${Pri.BehovType.TRENGER_FORESPØRSEL}.")
                sikkerLogger.info("Publiserte melding på pri-topic:\n${it.toPretty()}")
            }
            .onFailure {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${Pri.BehovType.TRENGER_FORESPØRSEL}.")
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
