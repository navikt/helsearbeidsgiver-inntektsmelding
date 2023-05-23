package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonLink
import no.nav.helsearbeidsgiver.felles.PreutfyltLøsning
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class HentPreutfyltLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BehovType.PREUTFYLL)
                it.rejectKey(Key.LØSNING.str)
                it.requireKey(Key.SESSION.str)
            }
        }.register(this)
    }

    fun hentLøsning(packet: JsonMessage): HentTrengerImLøsning =
        try {
            packet[Key.SESSION.str][BehovType.HENT_TRENGER_IM.name]
                .toJsonElement()
                .fromJson(HentTrengerImLøsning.serializer())
        } catch (ex: Exception) {
            sikkerLogger.error("Det oppstod en feil ved henting av session data for ${BehovType.HENT_TRENGER_IM}", ex)
            logger.error("Klarte ikke hente ut session data for ${BehovType.HENT_TRENGER_IM}")
            HentTrengerImLøsning(error = Feilmelding("Klarte ikke hente ut løsning"))
        }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("Fikk preutfylt pakke ${packet.toJson()}")
        val hentTrengerImLøsning = hentLøsning(packet)
        logger.info("Fikk preutfylt pakke")
        sikkerLogger.info("Fikk trenger løsning: ${hentTrengerImLøsning.error}")
        hentTrengerImLøsning.error?.let {
            sikkerLogger.error("Fant løsning med feil: ${it.melding}")
            packet[Key.LØSNING.str] = mapOf(
                BehovType.PREUTFYLL to PreutfyltLøsning(error = Feilmelding("Klarte ikke hente informasjon fra link"))
            )
        }
        hentTrengerImLøsning.value?.let {
            sikkerLogger.info("Fant løsning: $hentTrengerImLøsning")
            packet[Key.IDENTITETSNUMMER.str] = it.fnr
            packet[Key.ORGNRUNDERENHET.str] = it.orgnr
            packet[Key.NESTE_BEHOV.str] = listOf(
                BehovType.VIRKSOMHET.name,
                BehovType.FULLT_NAVN.name,
                BehovType.INNTEKT.name,
                BehovType.ARBEIDSFORHOLD.name
            )
            packet[Key.LØSNING.str] = mapOf(
                BehovType.PREUTFYLL to PreutfyltLøsning(value = PersonLink(it.fnr, it.orgnr))
            )
        }
        context.publish(packet.toJson())
    }
}
