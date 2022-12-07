@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NotifikasjonLøsning
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NotifikasjonLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    private val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-notifikasjon")
    private val BEHOV = BehovType.NOTIFIKASJON

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.interestedIn(Key.ID.str)
                it.interestedIn(Key.IDENTITETSNUMMER.str)
                it.interestedIn("orgnrUnderenhet")
                it.interestedIn("uuid")
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    fun opprettNotifikasjon(uuid: String, orgnr: String): String {
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                grupperingsid = uuid,
                merkelapp = "Inntektsmelding",
                virksomhetsnummer = orgnr,
                tittel = "Mottatt inntektsmelding",
                lenke = "$linkUrl/im-dialog/kvittering/$uuid",
                harddeleteOm = "P5M"
            )
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["uuid"].asText()
        logger.info("Fikk notifikasjon $uuid")
        val identitetsnummer = packet["identitetsnummer"].asText()
        val orgnrUnderenhet = packet["orgnrUnderenhet"].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            val notifikasjonId = opprettNotifikasjon(uuid, orgnrUnderenhet)
            publiserLøsning(NotifikasjonLøsning(notifikasjonId), packet, context)
            sikkerlogg.info("Sendte notifikasjon id=$notifikasjonId for $identitetsnummer")
            logger.info("Sendte notifikasjon for $uuid")
        } catch (ex: Exception) {
            sikkerlogg.error("Det oppstod en feil ved sending til $identitetsnummer for orgnr: $orgnrUnderenhet", ex)
            publiserLøsning(NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")), packet, context)
            logger.info("Klarte ikke sende notifikasjon for $uuid")
        }
    }

    fun publiserLøsning(løsning: NotifikasjonLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
