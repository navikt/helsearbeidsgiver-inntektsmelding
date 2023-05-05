package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ifFalse
import no.nav.helsearbeidsgiver.felles.ifTrue
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.list
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandAll
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import org.slf4j.LoggerFactory

class TrengerForespoerselLøser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        loggerSikker.info("Starting TrengerForespoerselLøser...")
        River(rapid).apply {
            validate { msg ->
                msg.demandAll(Key.BEHOV, listOf(BehovType.HENT_TRENGER_IM))
                msg.rejectKeys(Key.LØSNING)
                msg.require(
                    Key.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) }
                )
                msg.requireKeys(Key.BOOMERANG)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok behov om ${Key.BEHOV.fra(packet).fromJson(BehovType.serializer().list())}")
        loggerSikker.info("Mottok behov:\n${packet.toJson()}")

        val trengerForespoersel = TrengerForespoersel(
            forespoerselId = Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer),
            boomerang = Key.BOOMERANG.fra(packet)
        )
        logger.info("Sending message to helsebro for " + trengerForespoersel.forespoerselId + " current time" + System.currentTimeMillis())

        priProducer.send(trengerForespoersel)
            .ifTrue {
                logger.info("Publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
                loggerSikker.info("Publiserte melding på pri-topic:\n${trengerForespoersel.toJson(TrengerForespoersel.serializer())}")
            }
            .ifFalse {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
            }
    }
}
