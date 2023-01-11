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
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import org.slf4j.LoggerFactory

class VirksomhetLøser(rapidsConnection: RapidsConnection, private val brregClient: BrregClient, private val isPreProd: Boolean) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.VIRKSOMHET

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.ID.str)
                it.requireKey(Key.ORGNRUNDERENHET.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    fun hentVirksomhet(orgnr: String): String {
        if (isPreProd) {
            when (orgnr) {
                "810007702" -> return "ANSTENDIG PIGGSVIN BYDEL"
                "810007842" -> return "ANSTENDIG PIGGSVIN BARNEHAGE"
                "810008032" -> return "ANSTENDIG PIGGSVIN BRANNVESEN"
                "810007982" -> return "ANSTENDIG PIGGSVIN SYKEHJEM"
            }
            return "Ukjent arbeidsgiver"
        }
        return runBlocking { brregClient.hentVirksomhetNavn(orgnr) } ?: throw FantIkkeVirksomhetException(orgnr)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet[Key.ID.str].asText()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        try {
            val navn = hentVirksomhet(orgnr)
            sikkerlogg.info("Fant $navn for $orgnr")
            publiserLøsning(VirksomhetLøsning(navn), packet, context)
        } catch (ex: FantIkkeVirksomhetException) {
            sikkerlogg.info("Fant ikke virksomhet for $orgnr")
            publiserLøsning(VirksomhetLøsning(error = Feilmelding("Ugyldig virksomhet $orgnr")), packet, context)
        } catch (ex: Exception) {
            sikkerlogg.info("Det oppstod en feil ved henting for $orgnr")
            sikkerlogg.error(ex.stackTraceToString())
            publiserLøsning(VirksomhetLøsning(error = Feilmelding("Klarte ikke hente virksomhet")), packet, context)
        }
    }

    fun publiserLøsning(virksomhetLøsning: VirksomhetLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, virksomhetLøsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
