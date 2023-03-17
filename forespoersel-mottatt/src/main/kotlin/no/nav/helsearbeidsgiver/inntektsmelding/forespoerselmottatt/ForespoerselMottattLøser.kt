package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer

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
        logger.info("Mottok melding på pri-topic om ${Pri.Key.NOTIS.fra(packet).fromJson(Pri.NotisType.serializer())}.")
        loggerSikker.info("Mottok melding på pri-topic:\n${packet.toJson()}")

        val orgnr = Pri.Key.ORGNR.fra(packet).fromJson(String.serializer())
        val fnr = Pri.Key.FNR.fra(packet).fromJson(String.serializer())
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer)

        context.publish(
            Key.EVENT_NAME to EventName.FORESPØRSEL_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.NOTIFIKASJON_TRENGER_IM.toJson(BehovType.serializer()),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.IDENTITETSNUMMER to fnr.toJson(),
            Key.UUID to forespoerselId.toJson()
        )

        logger.info("Publiserte behov om '${BehovType.NOTIFIKASJON_TRENGER_IM}' med uuid (forespørsel-ID-en) '$forespoerselId'.")
    }
}
