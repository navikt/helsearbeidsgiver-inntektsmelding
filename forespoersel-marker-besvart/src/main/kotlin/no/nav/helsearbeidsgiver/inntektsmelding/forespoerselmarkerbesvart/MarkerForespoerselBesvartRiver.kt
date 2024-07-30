package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class MarkerForespoerselBesvartRiver(
    rapid: RapidsConnection,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid)
            .validate { msg ->
                msg.demandValues(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.name,
                )
                msg.require(
                    Key.UUID to { it.fromJson(UuidSerializer) },
                    Key.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) },
                )
                msg.rejectKeys(
                    Key.BEHOV,
                    Key.DATA,
                    Key.FAIL,
                )
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        if (packet[Key.FORESPOERSEL_ID.str].asText().isEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }
        val json = packet.toJson().parseJson()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.INNTEKTSMELDING_MOTTATT),
        ) {
            logger.info("Mottok melding om ${EventName.INNTEKTSMELDING_MOTTATT}.")
            sikkerLogger.info("Mottok melding:\n${json.toPretty()}.")

            val jsonMap = json.toMap()

            val transaksjonId = Key.UUID.les(UuidSerializer, jsonMap)
            val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, jsonMap)

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId),
            ) {
                sendMeldingOmBesvarelse(forespoerselId)
            }
        }
    }

    private fun sendMeldingOmBesvarelse(forespoerselId: UUID) {
        priProducer
            .send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            ).onSuccess {
                logger.info("Publiserte melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}.")
                sikkerLogger.info("Publiserte melding på pri-topic:\n${it.toPretty()}")
            }.onFailure {
                logger.error("Klarte ikke publiserte melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}.")
            }
    }
}
