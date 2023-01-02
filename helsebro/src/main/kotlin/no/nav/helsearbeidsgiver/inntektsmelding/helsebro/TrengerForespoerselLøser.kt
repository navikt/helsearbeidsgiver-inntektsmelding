package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
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
                it.requireKeys(
                    Key.UUID,
                    Key.VEDTAKSPERIODE_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok behov om ${packet.value(Key.BEHOV).asText()}")
        loggerSikker.info("Mottok behov:\n${packet.toJson()}")

        val trengerForespoersel = TrengerForespoersel(
            vedtaksperiodeId = Key.VEDTAKSPERIODE_ID.let(packet::value).asUuid(),
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
