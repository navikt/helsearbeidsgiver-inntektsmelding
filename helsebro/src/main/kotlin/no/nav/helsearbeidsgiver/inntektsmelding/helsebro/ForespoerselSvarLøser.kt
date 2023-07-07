package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
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

        val loesning = forespoerselSvar.toHentTrengerImLøsning()
        val resultat = loesning.value

        when {
            initiateEvent != EventName.TRENGER_REQUESTED -> {
                context.publish(
                    Key.EVENT_NAME to initiateEvent.toJson(),
                    Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
                    Key.LØSNING to mapOf(
                        BehovType.HENT_TRENGER_IM to loesning
                    ).toJson(),
                    Key.BOOMERANG to forespoerselSvar.boomerang
                )
                logger.info("Publiserte løsning for [${BehovType.HENT_TRENGER_IM}].")
            }

            resultat != null -> {
                context.publish(
                    Key.EVENT_NAME to initiateEvent.toJson(),
                    Key.DATA to "".toJson(),
                    Key.UUID to transactionID,
                    DataFelt.FORESPOERSEL_SVAR to resultat.toJson(TrengerInntekt.serializer())
                )

                logger.info("Recieve answer from helsebro for " + forespoerselSvar.forespoerselId + " current time" + System.currentTimeMillis())
                logger.info("Publiserte data for [${BehovType.HENT_TRENGER_IM}].")
            }

            else -> {
                val feil = loesning.error?.melding ?: FEIL_TOMT_SVAR

                context.publish(
                    Fail(
                        eventName = initiateEvent,
                        behov = BehovType.HENT_TRENGER_IM,
                        feilmelding = feil,
                        forespørselId = forespoerselSvar.forespoerselId.toString(),
                        uuid = transactionID.toJsonNode().asText()
                    ).toJsonMessage().toJson()
                )
            }
        }
    }
}

fun ForespoerselSvar.toHentTrengerImLøsning(): HentTrengerImLøsning =
    when {
        resultat != null -> {
            HentTrengerImLøsning(
                value = TrengerInntekt(
                    orgnr = resultat.orgnr,
                    fnr = resultat.fnr,
                    sykmeldingsperioder = resultat.sykmeldingsperioder,
                    egenmeldingsperioder = resultat.egenmeldingsperioder,
                    forespurtData = resultat.forespurtData
                )
            )
        }
        feil != null -> {
            HentTrengerImLøsning(error = Feilmelding("Klarte ikke hente forespørsel. Feilet med kode '$feil'."))
        }
        else -> {
            HentTrengerImLøsning(error = Feilmelding(FEIL_TOMT_SVAR))
        }
    }

private fun Map<BehovType, HentTrengerImLøsning>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            BehovType.serializer(),
            HentTrengerImLøsning.serializer()
        )
    )
