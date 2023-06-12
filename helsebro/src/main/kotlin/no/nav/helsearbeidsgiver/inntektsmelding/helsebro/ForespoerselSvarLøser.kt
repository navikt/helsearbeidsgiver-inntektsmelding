package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.require
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger

class ForespoerselSvarLøser(rapid: RapidsConnection) : River.PacketListener {

    private val logger = logger()
    init {
        sikkerLogger.info("Starting ForespoerselSvarLøser...")
        River(rapid).apply {
            validate { msg ->
                msg.demandValue(Pri.Key.BEHOV, ForespoerselSvar.behovType)
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

        val forespoerselSvar = Pri.Key.LØSNING.let(::value)
            .toJsonElement()
            .fromJson(ForespoerselSvar.serializer())

        sikkerLogger.info("Oversatte melding:\n$forespoerselSvar")

        val initiateEvent = forespoerselSvar.boomerang.fromJsonMap(String.serializer())[Key.INITIATE_EVENT.str]
            ?: throw IllegalArgumentException("Mangler ${Key.INITIATE_EVENT} i ${Key.BOOMERANG}.")
        context.publish(
            Key.EVENT_NAME to initiateEvent,
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.LØSNING to mapOf(
                BehovType.HENT_TRENGER_IM to forespoerselSvar.toHentTrengerImLøsning()
            )
                .toJson(
                    MapSerializer(
                        BehovType.serializer(),
                        HentTrengerImLøsning.serializer()
                    )
                ),
            Key.BOOMERANG to forespoerselSvar.boomerang
        )
        logger.info("Recieve answer from helsebro for " + forespoerselSvar.forespoerselId + " current time" + System.currentTimeMillis())
        logger.info("Publiserte løsning for [${BehovType.HENT_TRENGER_IM}].")
    }
}

fun ForespoerselSvar.toHentTrengerImLøsning(): HentTrengerImLøsning =
    if (resultat != null) {
        HentTrengerImLøsning(
            value = TrengerInntekt(
                orgnr = resultat.orgnr,
                fnr = resultat.fnr,
                sykmeldingsperioder = resultat.sykmeldingsperioder,
                egenmeldingsperioder = resultat.egenmeldingsperioder,
                forespurtData = resultat.forespurtData
            )
        )
    } else if (feil != null) {
        HentTrengerImLøsning(error = Feilmelding("Klarte ikke hente forespørsel. Feilet med kode '$feil'."))
    } else {
        HentTrengerImLøsning(error = Feilmelding("Svar fra bro-appen har hverken resultat eller feil."))
    }
