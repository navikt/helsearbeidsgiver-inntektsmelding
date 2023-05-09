package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import org.slf4j.LoggerFactory

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLøser(
    val rapid: RapidsConnection
) : River.PacketListener {

    private val om = customObjectMapper()
    private val logger = LoggerFactory.getLogger(this::class.java)

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
        logger.info("ForespoerselMottattLøser: Mottok melding på pri-topic om ${Pri.Key.NOTIS.fra(packet).fromJson(Pri.NotisType.serializer())}.")
        loggerSikker.info("ForespoerselMottattLøser: Mottok melding på pri-topic:\n${packet.toJson()}")

        val orgnr = Pri.Key.ORGNR.fra(packet).fromJson(String.serializer())
        val fnr = Pri.Key.FNR.fra(packet).fromJson(String.serializer())
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer)

        val msg = mapOf(
            Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT.name,
            Key.BEHOV.str to BehovType.LAGRE_FORESPOERSEL,
            Key.ORGNRUNDERENHET.str to orgnr,
            Key.IDENTITETSNUMMER.str to fnr,
            Key.FORESPOERSEL_ID.str to forespoerselId
        )

        val json = om.writeValueAsString(msg)
        rapid.publish(json)
        loggerSikker.info("ForespoerselMottattLøser: publiserte $json")
        logger.info("ForespoerselMottattLøser: ferdig")
    }
}
