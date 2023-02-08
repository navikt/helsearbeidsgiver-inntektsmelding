package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.valueNullable
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvarFeil
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvarSuksess

class ForespoerselSvarLøser(rapid: RapidsConnection) : River.PacketListener {
    init {
        River(rapid).apply {
            validate { jsonMessage ->
                jsonMessage.demandValue(Pri.Key.LØSNING, Pri.BehovType.TRENGER_FORESPØRSEL)
                jsonMessage.requireKeys(
                    Pri.Key.FORESPOERSEL_ID,
                    Pri.Key.BOOMERANG
                )
                jsonMessage.interestedIn(
                    Pri.Key.RESULTAT to { it.fromJson<ForespoerselSvarSuksess>() },
                    Pri.Key.FEIL to { it.fromJson<ForespoerselSvarFeil>() }
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok løsning på pri-topic om ${packet.value(Pri.Key.LØSNING).asText()}.")
        loggerSikker.info("Mottok løsning på pri-topic:\n${packet.toJson()}")

        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = Pri.Key.FORESPOERSEL_ID.let(packet::value).asUuid(),
            resultat = Pri.Key.RESULTAT.let(packet::valueNullable)?.toJsonElement()?.fromJson(),
            feil = Pri.Key.FEIL.let(packet::valueNullable)?.toJsonElement()?.fromJson(),
            boomerang = Pri.Key.BOOMERANG.let(packet::value).toJsonElement().fromJson()
        )

        loggerSikker.info("Oversatte melding:\n$forespoerselSvar")

        val initiateId = forespoerselSvar.boomerang[Key.INITIATE_ID.str]
            ?.fromJson(UuidSerializer)

        if (initiateId == null) {
            logger.error("Kan ikke svare på behov pga. manglende 'initiateId' i 'boomerang'-objekt.")
        } else {
            context.publish(
                Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType::toJson),
                Key.LØSNING to mapOf(
                    BehovType.HENT_TRENGER_IM to forespoerselSvar.toHentTrengerImLøsning()
                ).let(Json::encodeToJsonElement),
                Key.UUID to initiateId.toJson()
            )

            logger.info("Publiserte løsning for [${BehovType.HENT_TRENGER_IM}].")
        }
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
        HentTrengerImLøsning(error = Feilmelding(feil.feilmelding))
    } else {
        HentTrengerImLøsning(error = Feilmelding("Ukjent feil."))
    }
