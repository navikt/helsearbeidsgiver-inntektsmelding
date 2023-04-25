package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.require
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import org.slf4j.LoggerFactory

class ForespoerselSvarLøser(rapid: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    init {
        loggerSikker.info("Starting ForespoerselSvarLøser...")
        River(rapid).apply {
            validate { jsonMessage ->
                jsonMessage.demandValue(Pri.Key.BEHOV, ForespoerselSvar.behovType)
                jsonMessage.require(
                    Pri.Key.LØSNING to { it.fromJson(ForespoerselSvar.serializer()) }
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok løsning på pri-topic om ${packet.value(Pri.Key.BEHOV).asText()}.")
        loggerSikker.info("Mottok løsning på pri-topic:\n${packet.toJson()}")

        val forespoerselSvar = Pri.Key.LØSNING.let(packet::value)
            .toJsonElement()
            .fromJson(ForespoerselSvar.serializer())

        loggerSikker.info("Oversatte melding:\n$forespoerselSvar")
        val initiateEvent = forespoerselSvar.boomerang.toJsonNode().get(Key.INITIATE_EVENT.str).asText()
        context.publish(
            Key.EVENT_NAME to initiateEvent.toJson(),
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
                forespurtData = resultat.forespurtData
            )
        )
    } else if (feil != null) {
        HentTrengerImLøsning(error = Feilmelding("Klarte ikke hente forespørsel. Feilet med kode '$feil'."))
    } else {
        HentTrengerImLøsning(error = Feilmelding("Ukjent feil."))
    }
