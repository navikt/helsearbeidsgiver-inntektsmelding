package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger

class ForespoerselSvarLøser(rapid: RapidsConnection) : River.PacketListener {

    private val logger = logger()

    init {
        sikkerLogger.info("Starting ForespoerselSvarLøser...")
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(
                    Pri.Key.BEHOV to ForespoerselSvar.behovType.name
                )
                msg.require(
                    Pri.Key.LØSNING to { it.fromJson(ForespoerselSvar.serializer()) }
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.HENT_TRENGER_IM)
        ) {
            runCatching {
                json.sendSvar(context)
            }
                .onFailure { feil ->
                    "Ukjent feil.".let {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, feil)
                    }
                }
        }
    }

    private fun JsonElement.sendSvar(context: MessageContext) {
        val melding = toMap()

        logger.info("Mottok løsning på pri-topic om ${ForespoerselSvar.behovType}.")
        sikkerLogger.info("Mottok løsning på pri-topic:\n${toPretty()}")

        val forespoerselSvar = Pri.Key.LØSNING.les(ForespoerselSvar.serializer(), melding)

        sikkerLogger.info("Oversatte melding:\n$forespoerselSvar")

        val boomerangMap = forespoerselSvar.boomerang.toMap()

        val initiateEvent = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
        val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)

        MdcUtils.withLogFields(
            Log.event(initiateEvent),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselSvar.forespoerselId)
        ) {
            if (forespoerselSvar.resultat != null) {
                val trengerInntekt = forespoerselSvar.resultat.toTrengerInntekt()

                context.publish(
                    Key.EVENT_NAME to initiateEvent.toJson(),
                    Key.DATA to "".toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    DataFelt.FORESPOERSEL_SVAR to trengerInntekt.toJson(TrengerInntekt.serializer())
                )
                    .also {
                        logger.info("Publiserte data for ${BehovType.HENT_TRENGER_IM}.")
                        sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
                    }
            } else {
                val feilmelding = if (forespoerselSvar.feil != null) {
                    "Klarte ikke hente forespørsel. Feilet med kode '${forespoerselSvar.feil}'."
                } else {
                    "Svar fra bro-appen har hverken resultat eller feil.".also {
                        sikkerLogger.error(it)
                    }
                }

                Fail(
                    eventName = initiateEvent,
                    behov = BehovType.HENT_TRENGER_IM,
                    feilmelding = feilmelding,
                    forespørselId = forespoerselSvar.forespoerselId.toString(),
                    uuid = transaksjonId.toString()
                )
                    .toJsonMessage()
                    .toJson()
                    .also(context::publish)
                    .also {
                        logger.warn("Publiserte feil for ${BehovType.HENT_TRENGER_IM}.")
                        sikkerLogger.warn("Publiserte feil:\n${it.parseJson().toPretty()}")
                    }
            }
        }
    }
}

fun ForespoerselSvar.Suksess.toTrengerInntekt(): TrengerInntekt =
    TrengerInntekt(
        type = type,
        orgnr = orgnr,
        fnr = fnr,
        skjaeringstidspunkt = skjaeringstidspunkt,
        sykmeldingsperioder = sykmeldingsperioder,
        egenmeldingsperioder = egenmeldingsperioder,
        forespurtData = forespurtData,
        erBesvart = erBesvart
    )
