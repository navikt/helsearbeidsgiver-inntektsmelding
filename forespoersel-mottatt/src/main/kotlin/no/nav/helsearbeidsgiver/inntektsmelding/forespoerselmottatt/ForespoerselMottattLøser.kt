package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLøser(
    rapid: RapidsConnection,
    val forespoerselDao: ForespoerselDao
) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                it.demandValue(Pri.Key.NOTIS, Pri.NotisType.FORESPØRSEL_MOTTATT)
                it.requireKeys(
                    Pri.Key.ORGNR,
                    Pri.Key.FNR,
                    Pri.Key.VEDTAKSPERIODE_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding på pri-topic om ${packet.value(Pri.Key.NOTIS).asText()}.")
        loggerSikker.info("Mottok melding på pri-topic:\n${packet.toJson()}")

        val orgnr = Pri.Key.ORGNR.let(packet::value).asText()
        val fnr = Pri.Key.FNR.let(packet::value).asText()
        val vedtaksperiodeId = Pri.Key.VEDTAKSPERIODE_ID.let(packet::value).asUuid()

        val forespoerselId = forespoerselDao.lagre(vedtaksperiodeId)

        context.publish(
            // TODO burde være notis
            Key.BEHOV to listOf(BehovType.NOTIFIKASJON_TRENGER_IM),
            Key.ORGNRUNDERENHET to orgnr,
            Key.IDENTITETSNUMMER to fnr,
//            Key.UUID to forespoerselId // TODO kan brukes når vi kan mappe forespoerselId tilbake til vedtaksperiodeId
            Key.UUID to vedtaksperiodeId
        )

        logger.info("Publiserte behov om '${BehovType.NOTIFIKASJON_TRENGER_IM}' med uuid (vedtaksperiode-ID-en) '$vedtaksperiodeId'.")
    }
}
