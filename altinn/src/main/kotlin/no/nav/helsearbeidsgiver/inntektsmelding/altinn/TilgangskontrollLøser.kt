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
import no.nav.helsearbeidsgiver.felles.log.loggerSikker
import org.slf4j.LoggerFactory

class TilgangskontrollLøser(rapidsConnection: RapidsConnection, val altinnClient: AltinnClient) : River.PacketListener {

    private val BEHOV = BehovType.TILGANGSKONTROLL
    private val logger = LoggerFactory.getLogger(this::class.java)

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
        loggerSikker().info("Fikk pakke ${packet.toJson()}")
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Ber om tilgangskontroll for $forespørselId")
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgNr = packet[Key.SESSION.str].get(BehovType.HENT_IM_ORGNR.name).get("value").asText()
        val harTilgang = runBlocking {
            altinnClient.harRettighetForOrganisasjon(fnr, orgNr)
        }
        if (!harTilgang) {
            logger.info("Tilgang nektet for $forespørselId")
            publiserLøsning(TilgangskontrollLøsning(error = Feilmelding("Du har ikke rettigheter til å se på denne.")), packet, context)
        } else {
            logger.info("Tilgang godkjent for $forespørselId.")
            packet[Key.BOOMERANG.str] = mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.HENT_TRENGER_IM.name)
            )
            publiserLøsning(TilgangskontrollLøsning(orgNr), packet, context)
        }
    }

    fun publiserLøsning(løsning: TilgangskontrollLøsning, packet: JsonMessage, context: MessageContext) {
        packet[Key.LØSNING.str] = mapOf(
            BEHOV.name to løsning
        )
        context.publish(packet.toJson())
    }
}
