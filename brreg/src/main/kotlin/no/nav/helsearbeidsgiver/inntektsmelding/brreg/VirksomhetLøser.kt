@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import org.slf4j.LoggerFactory

class VirksomhetLøser(rapidsConnection: RapidsConnection, private val brregClient: BrregClient) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.VIRKSOMHET

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey("orgnrUnderenhet")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    fun hentVirksomhet(orgnr: String): String {
        return runBlocking { brregClient.hentVirksomhetNavn(orgnr) } ?: throw FantIkkeVirksomhetException(orgnr)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val orgnr = packet["orgnrUnderenhet"].asText()
        try {
            val navn = hentVirksomhet(orgnr)
            sikkerlogg.info("Fant $navn for $orgnr")
            packet.setLøsning(BEHOV, VirksomhetLøsning(navn))
            context.publish(packet.toJson())
        } catch (ex: FantIkkeVirksomhetException) {
            packet.setLøsning(BEHOV, VirksomhetLøsning(error = Feilmelding("Ugyldig virksomhet $orgnr")))
            sikkerlogg.info("Fant ikke virksomhet for $orgnr")
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, VirksomhetLøsning(error = Feilmelding("Klarte ikke hente virksomhet")))
            sikkerlogg.info("Det oppstod en feil ved henting for $orgnr")
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
