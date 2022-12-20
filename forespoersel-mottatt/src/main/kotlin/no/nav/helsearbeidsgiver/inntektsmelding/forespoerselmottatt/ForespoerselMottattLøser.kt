package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.value

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLøser(
    rapid: RapidsConnection
) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                // TODO i dag er nøkkel "eventType"
                it.demandValue(Key.NOTIS, BehovType.FORESPØRSEL_MOTTATT)
                it.requireKeys(
                    Key.ORGNR,
                    Key.FNR,
                    Key.VEDTAKSPERIODE_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding om ${packet.value(Key.NOTIS).asText()}")
        loggerSikker.info("Mottok melding:\n${packet.toJson()}")

        val orgnr = packet.value(Key.ORGNR).asText()
        val fnr = packet.value(Key.FNR).asText()
        val vedtaksperiodeId = packet.value(Key.VEDTAKSPERIODE_ID).asUuid()

//        val uuid = UUID.randomUUID()

//        forespoerselDao.lagre(uuid, vedtaksperiodeId)

        context.publish(
            Key.BEHOV to listOf(BehovType.NOTIFIKASJON_TRENGER_IM),
            Key.ORGNRUNDERENHET to orgnr,
            Key.IDENTITETSNUMMER to fnr,
            Key.UUID to vedtaksperiodeId
        )

        logger.info("Publiserte behov om '${BehovType.NOTIFIKASJON_TRENGER_IM}' med uuid (vedtaksperiode-ID-en) '$vedtaksperiodeId'.")
    }
}
