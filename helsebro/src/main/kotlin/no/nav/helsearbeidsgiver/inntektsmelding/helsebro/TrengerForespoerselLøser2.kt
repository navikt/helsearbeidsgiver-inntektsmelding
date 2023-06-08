package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue

class TrengerForespoerselLøser2(
    rapid: RapidsConnection,
    private val priProducer: PriProducer
) : Løser(rapid) {

    private val logger = logger()

    init {
        sikkerLogger.info("Starting TrengerForespoerselLøser...")
    }

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.demandValue(Key.BEHOV to BehovType.HENT_TRENGER_IM)
        it.interestedIn(
            Key.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) }
        )
        it.interestedIn(Key.BOOMERANG)
    }

    override fun onBehov(packet: JsonMessage) {
        logger.info("Mottok behov om ${Key.BEHOV.fra(packet).fromJson(BehovType.serializer().list())}")
        sikkerLogger.info("Mottok behov:\n${packet.toJson()}")

        val trengerForespoersel = TrengerForespoersel(
            forespoerselId = Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer),
            boomerang = Key.BOOMERANG.fra(packet)
        )
        logger.info("Sending message to helsebro for " + trengerForespoersel.forespoerselId + " current time" + System.currentTimeMillis())

        priProducer.send(trengerForespoersel)
            .ifTrue {
                logger.info("Publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
                sikkerLogger.info("Publiserte melding på pri-topic:\n${trengerForespoersel.toJson(TrengerForespoersel.serializer())}")
            }
            .ifFalse {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${trengerForespoersel.behov}.")
            }
    }
}
