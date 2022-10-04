@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.syk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SykLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = Behov.SYK.name

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", listOf(BEHOV))
                it.requireKey("@id")
                it.requireKey("identitetsnummer")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val fnr = packet["identitetsnummer"].asText()
        try {
            val fra = LocalDate.of(2022, 10, 5)
            val fravaersperiode = mutableMapOf<String, List<MottattPeriode>>()
            fravaersperiode.put(fnr, listOf(MottattPeriode(fra, fra.plusDays(10))))
            val behandlingsperiode = MottattPeriode(fra, fra.plusDays(10))
            val syk = Syk(fravaersperiode, behandlingsperiode)
            packet.setLøsning(BEHOV, Løsning(BEHOV, syk))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant syk $syk for $fnr")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, Løsning(BEHOV, error = Feilmelding("Klarte ikke hente syk")))
            sikkerlogg.info("Det oppstod en feil ved henting av syk for $fnr")
            sikkerlogg.error(ex.stackTraceToString())
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
