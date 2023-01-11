package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.asUuid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.demandValue
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.value
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar

class ForespoerselSvarLøser(rapid: RapidsConnection) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                it.demandValue(Pri.Key.LØSNING, Pri.BehovType.TRENGER_FORESPØRSEL)
                it.requireKeys(
                    Pri.Key.ORGNR,
                    Pri.Key.FNR,
                    Pri.Key.VEDTAKSPERIODE_ID,
                    Pri.Key.SYKMELDINGSPERIODER,
                    Pri.Key.FORESPURT_DATA,
                    Pri.Key.BOOMERANG
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok løsning på pri-topic om ${packet.value(Pri.Key.LØSNING).asText()}.")
        loggerSikker.info("Mottok løsning på pri-topic:\n${packet.toJson()}")

        val forespoerselSvar = ForespoerselSvar(
            orgnr = Pri.Key.ORGNR.let(packet::value).asText(),
            fnr = Pri.Key.FNR.let(packet::value).asText(),
            vedtaksperiodeId = Pri.Key.VEDTAKSPERIODE_ID.let(packet::value).asUuid(),
            sykmeldingsperioder = Pri.Key.SYKMELDINGSPERIODER.let(packet::value).toString().let(Json::decodeFromString),
            forespurtData = Pri.Key.FORESPURT_DATA.let(packet::value).toString().let(Json::decodeFromString),
            boomerang = Pri.Key.BOOMERANG.let(packet::value).toString().let(Json::decodeFromString)
        )

        loggerSikker.info("Oversatte melding:\n$forespoerselSvar")

        val initiateId = forespoerselSvar.boomerang[Key.INITIATE_ID.str]
            ?.toUuid()
            ?: throw BoomerangContentException()

        context.publish(
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM),
            Key.LØSNING to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    value = TrengerInntekt(
                        orgnr = forespoerselSvar.orgnr,
                        fnr = forespoerselSvar.fnr,
                        sykemeldingsperioder = forespoerselSvar.sykmeldingsperioder,
                        egenmeldingsperioder = emptyList()
                    )
                )
            ),
            Key.UUID to initiateId
        )

        logger.info("Publiserte løsning for [${BehovType.HENT_TRENGER_IM}].")
    }
}
