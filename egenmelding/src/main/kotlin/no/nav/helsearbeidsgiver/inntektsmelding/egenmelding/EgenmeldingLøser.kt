@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.egenmelding

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EgenmeldingLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Periode
import org.slf4j.LoggerFactory
import java.time.LocalDate

class EgenmeldingLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.EGENMELDING

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey("identitetsnummer")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    fun hentPerioder(identitetsnummer: String): List<Periode> {
        if (identitetsnummer.endsWith("000000000")) {
            throw RuntimeException("Identitestnummer kan ikke være 000000000")
        }
        return listOf(Periode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2)))
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val identitetsnummer = packet["identitetsnummer"].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            val perioder = hentPerioder(identitetsnummer)
            packet.setLøsning(BEHOV, EgenmeldingLøsning(perioder))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant syk $perioder for $identitetsnummer")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, EgenmeldingLøsning(error = Feilmelding("Klarte ikke hente egenmelding")))
            sikkerlogg.info("Det oppstod en feil ved henting av egenmelding for $identitetsnummer")
            sikkerlogg.error(ex.stackTraceToString())
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
