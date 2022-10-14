@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.syk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.MottattPeriode
import no.nav.helsearbeidsgiver.felles.Syk
import no.nav.helsearbeidsgiver.felles.SykLøsning
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SykLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.SYK

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

    fun hentSykdomsdata(identitetsnummer: String): Syk {
        if (identitetsnummer.endsWith("000000000")) {
            throw RuntimeException("Identitestnummer kan ikke være 000000000")
        }
        val fra = LocalDate.of(2022, 10, 5)
        val fravaersperiode = mutableMapOf<String, List<MottattPeriode>>()
        fravaersperiode.put(identitetsnummer, listOf(MottattPeriode(fra, fra.plusDays(10))))
        val behandlingsperiode = MottattPeriode(fra, fra.plusDays(10))
        return Syk(fravaersperiode, behandlingsperiode)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val identitetsnummer = packet["identitetsnummer"].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            val syk = hentSykdomsdata(identitetsnummer)
            packet.setLøsning(BEHOV, SykLøsning(syk))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant syk $syk for $identitetsnummer")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, SykLøsning(error = Feilmelding("Klarte ikke hente syk")))
            sikkerlogg.info("Det oppstod en feil ved henting av syk for $identitetsnummer")
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
