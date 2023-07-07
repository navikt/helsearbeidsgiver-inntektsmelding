package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger

private const val FEIL_TOMT_SVAR = "Svar fra bro-appen har hverken resultat eller feil."

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
        runCatching {
            packet.loesBehov(context)
        }
            .onFailure { feil ->
                "Ukjent feil.".let {
                    logger.error("$it Se sikker logg for mer info.")
                    sikkerLogger.error(it, feil)
                }
            }
    }

    private fun JsonMessage.loesBehov(context: MessageContext) {
        logger.info("Mottok løsning på pri-topic om ${Pri.Key.BEHOV.fra(this).fromJson(Pri.BehovType.serializer())}.")
        sikkerLogger.info("Mottok løsning på pri-topic:\n${toJson()}")

        val forespoerselSvar = Pri.Key.LØSNING.fra(this).fromJson(ForespoerselSvar.serializer())

        sikkerLogger.info("Oversatte melding:\n$forespoerselSvar")

        val initiateEvent = forespoerselSvar.boomerang.fromJsonMap(String.serializer())[Key.INITIATE_EVENT.str]
            ?.fromJson(EventName.serializer())
            ?: throw IllegalArgumentException("Mangler ${Key.INITIATE_EVENT} i ${Key.BOOMERANG}.")

        val transactionID = forespoerselSvar.boomerang.fromJsonMap(String.serializer())[Key.INITIATE_ID.str]
            ?: throw IllegalArgumentException("Mangler ${Key.INITIATE_ID} i ${Key.BOOMERANG}.")

        val foresportData = forespoerselSvar.toTrengerInntekt()

        if (foresportData != null) {
            context.publish(
                Key.EVENT_NAME to initiateEvent.toJson(),
                Key.DATA to "".toJson(),
                Key.UUID to transactionID,
                DataFelt.FORESPOERSEL_SVAR to foresportData.toJson(TrengerInntekt.serializer())
            )

            logger.info("Recieve answer from helsebro for " + forespoerselSvar.forespoerselId + " current time" + System.currentTimeMillis())
            logger.info("Publiserte data for [${BehovType.HENT_TRENGER_IM}].")
        } else {
            val feil = forespoerselSvar.extractFailmenlding()

            context.publish(
                Fail(
                    eventName = initiateEvent,
                    behov = BehovType.HENT_TRENGER_IM,
                    feilmelding = feil.toString(),
                    forespørselId = forespoerselSvar.forespoerselId.toString(),
                    uuid = transactionID.toJsonNode().asText()
                ).toJsonMessage().toJson()
            )
        }
    }
}

fun ForespoerselSvar.toTrengerInntekt(): TrengerInntekt? =

    this.resultat?.let {
        TrengerInntekt(
            orgnr = resultat.orgnr,
            fnr = resultat.fnr,
            sykmeldingsperioder = resultat.sykmeldingsperioder,
            egenmeldingsperioder = resultat.egenmeldingsperioder,
            forespurtData = resultat.forespurtData
        )
    }

fun ForespoerselSvar.extractFailmenlding(): Feilmelding? =
    when {
        feil != null -> {
            Feilmelding("Klarte ikke hente forespørsel. Feilet med kode '$feil'.")
        }
        resultat == null -> {
            Feilmelding(FEIL_TOMT_SVAR)
        }
        else -> {
            null
        }
    }
