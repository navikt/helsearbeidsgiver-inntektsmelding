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
import java.lang.IllegalArgumentException

class NotifikasjonLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    private val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-notifikasjon")

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAllOrAny(Key.BEHOV.str, listOf(BehovType.NOTIFIKASJON.name, BehovType.NOTIFIKASJON_TRENGER_IM.name))
                it.interestedIn(Key.ID.str)
                it.interestedIn(Key.IDENTITETSNUMMER.str)
                it.interestedIn(Key.ORGNRUNDERENHET.str)
                it.interestedIn("uuid")
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    fun trengerIM(uuid: String, orgnr: String): String {
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                grupperingsid = uuid,
                merkelapp = "Inntektsmelding",
                virksomhetsnummer = orgnr,
                tittel = "Trenger inntektsmelding",
                lenke = "$linkUrl/im-dialog/trenger/$uuid",
                harddeleteOm = "P5M"
            )
        }
    }

    fun mottattIM(uuid: String, orgnr: String): String {
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

    fun opprettNotifikasjon(behovType: BehovType, uuid: String, orgnr: String): String {
        if (behovType == BehovType.NOTIFIKASJON) {
            return mottattIM(uuid, orgnr)
        }
        if (behovType == BehovType.NOTIFIKASJON_TRENGER_IM) {
            return trengerIM(uuid, orgnr)
        }
        throw IllegalArgumentException("Ugyldig behov $behovType")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["uuid"].asText()
        val behovStr: String = packet["@behov"][0].textValue()
        logger.info("Behov: $behovStr")
        val behovType = BehovType.valueOf(behovStr)
        logger.info("Fikk notifikasjon $uuid for behov $behovType")
        val identitetsnummer = packet["identitetsnummer"].asText()
        val orgnrUnderenhet = packet[Key.ORGNRUNDERENHET.str].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            val notifikasjonId = opprettNotifikasjon(behovType, uuid, orgnrUnderenhet)
            publiserLøsning(behovType, NotifikasjonLøsning(notifikasjonId), packet, context)
            sikkerlogg.info("Sendte notifikasjon id=$notifikasjonId for $identitetsnummer")
            logger.info("Sendte notifikasjon for $uuid")
        } catch (ex: Exception) {
            sikkerlogg.error("Det oppstod en feil ved sending til $identitetsnummer for orgnr: $orgnrUnderenhet", ex)
            publiserLøsning(behovType, NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")), packet, context)
            logger.info("Klarte ikke sende notifikasjon for $uuid")
        }
    }

    fun publiserLøsning(behovType: BehovType, løsning: NotifikasjonLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(behovType, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
