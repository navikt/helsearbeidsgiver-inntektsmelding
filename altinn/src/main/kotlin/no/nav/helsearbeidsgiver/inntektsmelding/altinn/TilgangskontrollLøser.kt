package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TilgangskontrollLøser(rapidsConnection: RapidsConnection, val altinnClient: AltinnClient) : River.PacketListener {

    private val BEHOV = BehovType.TILGANGSKONTROLL
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")

    init {
        logger.info("Starter TilgangskontrollLøser...")
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.FORESPOERSEL_ID.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
                it.rejectKey(Key.LØSNING.str)
                it.requireKey(Key.SESSION.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("Fikk pakke ${packet.toJson()}")
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Ber om tilgangskontroll for $forespørselId")
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgNr = packet[Key.SESSION.str].get(BehovType.HENT_IM_ORGNR.name).get("value").asText()
        try {
            val harTilgang = runBlocking {
                altinnClient.harRettighetForOrganisasjon(fnr, orgNr)
            }
            if (!harTilgang) {
                logger.info("Tilgang nektet for $forespørselId")
                sikkerLogger.info("Tilgang nektet for $forespørselId mot orgnr: $orgNr for fnr: $fnr")
                publiserLøsning(TilgangskontrollLøsning(error = Feilmelding("Du har ikke rettigheter til å se på denne.")), packet, context)
            } else {
                logger.info("Tilgang godkjent for $forespørselId.")
                sikkerLogger.info("Tilgang godkjent for $forespørselId mot orgnr: $orgNr for fnr: $fnr")
                publiserLøsning(TilgangskontrollLøsning(orgNr), packet, context)
            }
        } catch (ex: Exception) {
            sikkerLogger.error("Det oppsted en feil ved kall mot Altinn", ex)
            logger.error("Det oppsted en feil ved kall mot Altinn")
            publiserLøsning(TilgangskontrollLøsning(error = Feilmelding("Du har ikke rettigheter til å se på denne.")), packet, context)
        }
    }

    fun publiserLøsning(løsning: TilgangskontrollLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
