package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

class HentPreutfyltLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BehovType.HENT_TRENGER_IM)
                it.requireKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val løsning = packet[Key.LØSNING.str]
        sikkerlogg.info("Fant løsning: $løsning")
        packet[Key.ORGNRUNDERENHET.str] = løsning.get("orgnr").asText()
        packet[Key.IDENTITETSNUMMER.str] = løsning.get("fnr").asText()
        packet[Key.NESTE_BEHOV.str] = listOf(
            BehovType.VIRKSOMHET.name,
            BehovType.FULLT_NAVN.name,
            BehovType.INNTEKT.name,
            BehovType.ARBEIDSFORHOLD.name,
            BehovType.EGENMELDING.name,
            BehovType.SYK.name
        )
        // context.publish(packet.toJson())
        sikkerlogg.info("Ville ha publisert: ${packet.toJson()}")
    }

}
