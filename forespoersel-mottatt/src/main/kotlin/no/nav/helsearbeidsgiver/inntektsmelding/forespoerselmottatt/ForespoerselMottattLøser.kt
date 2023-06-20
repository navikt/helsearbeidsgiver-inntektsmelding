package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLøser(
    rapid: RapidsConnection
) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.name
                )
                it.requireKeys(
                    Pri.Key.ORGNR,
                    Pri.Key.FNR,
                    Pri.Key.FORESPOERSEL_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("[ForespoerselMottattLøser] Mottok melding på pri-topic om ${Pri.Key.NOTIS.fra(packet).fromJson(Pri.NotisType.serializer())}.")
        sikkerLogger.info("[ForespoerselMottattLøser] Mottok melding på pri-topic:\n${packet.toJson()}")

        val orgnr = Pri.Key.ORGNR.fra(packet).fromJson(String.serializer())
        val fnr = Pri.Key.FNR.fra(packet).fromJson(String.serializer())
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer)

        context.publish(
            Key.EVENT_NAME to EventName.FORESPØRSEL_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.LAGRE_FORESPOERSEL.toJson(BehovType.serializer()),
            DataFelt.ORGNRUNDERENHET to orgnr.toJson(),
            Key.IDENTITETSNUMMER to fnr.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )
            .also {
                logger.info("[ForespoerselMottattLøser] Publiserte melding. Se sikkerlogg for mer info.")
                sikkerLogger.info("[ForespoerselMottattLøser] Publiserte:\n$it")
            }
    }
}
