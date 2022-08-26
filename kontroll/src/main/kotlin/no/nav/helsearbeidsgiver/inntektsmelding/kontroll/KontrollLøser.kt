package no.nav.helsearbeidsgiver.inntektsmelding.kontroll

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class KontrollLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    companion object {
        internal const val behov = "KontrollLøser"
    }

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "inntektsmelding_inn") }
            validate { it.requireContains("@behov", "JournalførInntektsmeldingLøser") }
            validate { it.requireKey("@løsning") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Inntektsmelding er ferdigbehandlet: $packet")
        // TODO - Publiser at inntektsmelding er publisert
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("Fikk error $problems")
    }
}
