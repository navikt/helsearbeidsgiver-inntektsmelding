package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NotisType
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLøser(
    rapid: RapidsConnection
) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                it.demandValue(Pri.Key.NOTIS, Pri.NotisType.FORESPØRSEL_MOTTATT)
                it.requireKeys(
                    Pri.Key.ORGNR,
                    Pri.Key.FNR,
                    Pri.Key.FORESPOERSEL_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding på pri-topic om ${packet.value(Pri.Key.NOTIS).asText()}.")
        loggerSikker.info("Mottok melding på pri-topic:\n${packet.toJson()}")

        val orgnr = Pri.Key.ORGNR.let(packet::value).asText()
        val fnr = Pri.Key.FNR.let(packet::value).asText()
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.let(packet::value).asUuid()

        context.publish(
            Key.NOTIS to listOf(NotisType.NOTIFIKASJON_TRENGER_IM).toJson(NotisType::toJson),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.IDENTITETSNUMMER to fnr.toJson(),
            Key.UUID to forespoerselId.toJson()
        )

        logger.info("Publiserte notis om '${NotisType.NOTIFIKASJON_TRENGER_IM}' med uuid (forespørsel-ID-en) '$forespoerselId'.")
    }
}
