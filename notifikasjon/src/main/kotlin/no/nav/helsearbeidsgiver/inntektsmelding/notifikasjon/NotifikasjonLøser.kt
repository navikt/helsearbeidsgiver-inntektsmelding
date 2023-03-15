@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NotifikasjonLøsning
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class NotifikasjonLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Løser(rapidsConnection) {

    private val sikkerLogger = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAny(Key.BEHOV.str, listOf(BehovType.NOTIFIKASJON_IM_MOTTATT.name, BehovType.NOTIFIKASJON_TRENGER_IM.name))
            it.interestedIn(Key.ID.str)
            it.interestedIn(Key.IDENTITETSNUMMER.str)
            it.interestedIn(Key.ORGNRUNDERENHET.str)
        }
    }

    fun trengerIM(
        uuid: String,
        orgnr: String,
        navn: String = "Ola Normann",
        fødselsdato: LocalDate = LocalDate.now(),
        tidspunkt: LocalDateTime = LocalDateTime.now()
    ): String {
        return runBlocking {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                grupperingsid = uuid,
                merkelapp = "Inntektsmelding",
                virksomhetsnummer = orgnr,
                tittel = "Inntektsmelding for $navn: f. $fødselsdato",
                lenke = "$linkUrl/im-dialog/$uuid",
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
        if (behovType == BehovType.NOTIFIKASJON_IM_MOTTATT) {
            return mottattIM(uuid, orgnr)
        }
        if (behovType == BehovType.NOTIFIKASJON_TRENGER_IM) {
            return trengerIM(uuid, orgnr)
        }
        throw IllegalArgumentException("Ugyldig behovType $behovType")
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        val behovType = BehovType.valueOf(packet[Key.BEHOV.str].asText())
        logger.info("BehovType: $behovType")
        logger.info("Fikk notifikasjon $uuid for notis $behovType")
        val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgnrUnderenhet = packet[Key.ORGNRUNDERENHET.str].asText()
        sikkerLogger.info("Fant notis for: $identitetsnummer")
        try {
            val notifikasjonId = opprettNotifikasjon(behovType, uuid, orgnrUnderenhet)
            publiserLøsning(behovType, NotifikasjonLøsning(notifikasjonId), packet)
            sikkerLogger.info("Sendte notifikasjon id=$notifikasjonId for $identitetsnummer")
            logger.info("Sendte notifikasjon for $uuid")
        } catch (ex: Exception) {
            sikkerLogger.error("Det oppstod en feil ved sending til $identitetsnummer for orgnr: $orgnrUnderenhet", ex)
            publiserLøsning(behovType, NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")), packet)
            logger.error("Klarte ikke sende notifikasjon for $uuid")
        }
    }

    fun publiserLøsning(behovType: BehovType, løsning: NotifikasjonLøsning, packet: JsonMessage) {
        packet.setLøsning(behovType, løsning)
        publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
