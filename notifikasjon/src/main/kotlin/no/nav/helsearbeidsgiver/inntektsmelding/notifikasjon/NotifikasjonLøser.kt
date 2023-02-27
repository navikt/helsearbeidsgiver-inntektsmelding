@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NotifikasjonLøsning
import no.nav.helsearbeidsgiver.felles.NotisType
import java.time.LocalDate
import java.time.LocalDateTime

class NotifikasjonLøser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAllOrAny(Key.NOTIS.str, listOf(NotisType.NOTIFIKASJON.name, NotisType.NOTIFIKASJON_TRENGER_IM.name))
                it.interestedIn(Key.ID.str)
                it.interestedIn(Key.IDENTITETSNUMMER.str)
                it.interestedIn(Key.ORGNRUNDERENHET.str)
                it.interestedIn(Key.UUID.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
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

    fun opprettNotifikasjon(notisType: NotisType, uuid: String, orgnr: String): String {
        if (notisType == NotisType.NOTIFIKASJON) {
            return mottattIM(uuid, orgnr)
        }
        if (notisType == NotisType.NOTIFIKASJON_TRENGER_IM) {
            return trengerIM(uuid, orgnr)
        }
        throw IllegalArgumentException("Ugyldig notis $notisType")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        val notisStr: String = packet[Key.NOTIS.str][0].textValue()
        logger.info("Notis: $notisStr")
        val notisType = NotisType.valueOf(notisStr)
        logger.info("Fikk notifikasjon $uuid for notis $notisType")
        val identitetsnummer = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgnrUnderenhet = packet[Key.ORGNRUNDERENHET.str].asText()
        sikkerlogg.info("Fant notis for: $identitetsnummer")
        try {
            val notifikasjonId = opprettNotifikasjon(notisType, uuid, orgnrUnderenhet)
            publiserLøsning(notisType, NotifikasjonLøsning(notifikasjonId), packet, context)
            sikkerlogg.info("Sendte notifikasjon id=$notifikasjonId for $identitetsnummer")
            logger.info("Sendte notifikasjon for $uuid")
        } catch (ex: Exception) {
            sikkerlogg.error("Det oppstod en feil ved sending til $identitetsnummer for orgnr: $orgnrUnderenhet", ex)
            publiserLøsning(notisType, NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")), packet, context)
            logger.info("Klarte ikke sende notifikasjon for $uuid")
        }
    }

    fun publiserLøsning(notisType: NotisType, løsning: NotifikasjonLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(notisType, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: NotisType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
