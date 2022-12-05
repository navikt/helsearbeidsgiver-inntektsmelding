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

class NotifikasjonLøser(rapidsConnection: RapidsConnection, private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient) : River.PacketListener {

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
            try {
                arbeidsgiverNotifikasjonKlient.opprettNySak(
                    grupperingsid = uuid,
                    merkelapp = "Inntektsmelding",
                    virksomhetsnummer = orgnr,
                    tittel = "Mottatt inntektsmelding",
                    lenke = "https://arbeidsgiver.dev.nav.no/im-dialog/kvittering/$uuid",
                    harddeleteOm = "dummy"
                )
            } catch (ex: Exception) {
                println(ex.printStackTrace())
                throw(ex)
            }
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val identitetsnummer = packet["identitetsnummer"].asText()
        val orgnrUnderenhet = packet["orgnrUnderenhet"].asText()
        val uuid = packet["uuid"].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            val notifikasjonId = opprettNotifikasjon(uuid, orgnrUnderenhet)
            publiserLøsning(NotifikasjonLøsning(notifikasjonId), packet, context)
            sikkerlogg.info("Sendte notifikasjon id=$notifikasjonId for $identitetsnummer")
        } catch (ex: Exception) {
            sikkerlogg.error("Det oppstod en feil ved sending til $identitetsnummer for orgnr: $orgnrUnderenhet", ex)
            publiserLøsning(NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")), packet, context)
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
