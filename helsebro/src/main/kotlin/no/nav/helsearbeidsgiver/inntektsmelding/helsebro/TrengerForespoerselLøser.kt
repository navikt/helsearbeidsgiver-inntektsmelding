package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ifFalse
import no.nav.helsearbeidsgiver.felles.ifTrue
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandAll
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireTypes
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel

class TrengerForespoerselLøser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer
) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                it.demandAll(Key.BEHOV, listOf(BehovType.HENT_TRENGER_IM))
                it.rejectKeys(Key.LØSNING)
                it.requireTypes(
                    Key.UUID to JsonNode::asUuid,
                    Key.FORESPOERSEL_ID to JsonNode::asUuid
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok behov om ${packet.value(Key.BEHOV).map(JsonNode::asText)}")
        loggerSikker.info("Mottok behov:\n${packet.toJson()}")

        val trengerForespoersel = TrengerForespoersel(
            forespoerselId = Key.FORESPOERSEL_ID.let(packet::value).asUuid(),
            boomerang = mapOf(
                Key.INITIATE_ID.str to Key.UUID.let(packet::value).asUuid().toJson()
            )
        )

        priProducer.send(trengerForespoersel)
            .ifTrue {
                logger.info("Publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
                loggerSikker.info("Publiserte melding på pri-topic:\n${trengerForespoersel.toJson()}")
            }
            .ifFalse {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
            }
    }
}
