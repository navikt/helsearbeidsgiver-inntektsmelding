@file:Suppress("NonAsciiCharacters", "ClassName")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import org.slf4j.LoggerFactory

class ArbeidsforholdLøser(
    rapidsConnection: RapidsConnection,
    private val aaregClient: AaregClient
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.ARBEIDSFORHOLD

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.ID.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    fun hentArbeidsforhold(fnr: String, callId: String): List<no.nav.helsearbeidsgiver.felles.Arbeidsforhold> {
        val arbeidsforhold = runBlocking { aaregClient.hentArbeidsforhold(fnr, callId) }
        return arbeidsforhold.map(Arbeidsforhold::tilArbeidsforhold)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = packet[Key.ID.str].asText()
        logger.info("Løser behov $BEHOV med id $id")
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        try {
            val arbeidsforhold = hentArbeidsforhold(fnr, id)
            packet.setLøsning(BEHOV, ArbeidsforholdLøsning(arbeidsforhold))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant arbeidsforhold $arbeidsforhold for $fnr")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, ArbeidsforholdLøsning(error = Feilmelding("Klarte ikke hente arbeidsforhold")))
            sikkerlogg.info("Det oppstod en feil ved henting av arbeidsforhold for $fnr")
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
