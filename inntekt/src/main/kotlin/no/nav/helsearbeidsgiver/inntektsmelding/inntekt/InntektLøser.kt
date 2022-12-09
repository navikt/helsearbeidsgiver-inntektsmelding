@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

fun finnStartMnd(now: LocalDate = LocalDate.now()): LocalDate? {
    return LocalDate.of(now.year, now.month, 1)
}

class InntektLøser(rapidsConnection: RapidsConnection, val inntektKlient: InntektKlient) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.INNTEKT

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey("identitetsnummer")
                it.requireKey(Key.ORGNRUNDERENHET.str)
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    private fun hentInntekt(fnr: String, fra: LocalDate, til: LocalDate, callId: String): InntektskomponentResponse {
        return runBlocking {
            sikkerlogg.info("Henter inntekt for $fnr i perioden $fra til $til (callId: $callId)")
            inntektKlient.hentInntektListe(fnr, callId, "helsearbeidsgiver-im-inntekt", fra, til, "8-28", "Sykepenger")
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["@id"].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        val fnr = packet["identitetsnummer"].asText()
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val til = finnStartMnd()
        val fra = til!!.minusMonths(9) // TODO: skal endres tilbake til 3 mnd
        try {
            val inntektResponse = hentInntekt(fnr, fra, til, "helsearbeidsgiver-im-inntekt-$uuid")
            val inntekt = mapInntekt(inntektResponse, orgnr)
            packet.setLøsning(BEHOV, InntektLøsning(inntekt))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant inntekt $inntekt for $fnr og orgnr $orgnr")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, InntektLøsning(error = Feilmelding("Klarte ikke hente inntekt")))
            sikkerlogg.info("Det oppstod en feil ved henting av inntekt for $fnr", ex)
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
