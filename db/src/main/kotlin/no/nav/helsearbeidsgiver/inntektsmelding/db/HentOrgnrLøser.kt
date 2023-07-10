@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentImOrgnrLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class HentOrgnrLøser(rapidsConnection: RapidsConnection, private val repository: ForespoerselRepository) : River.PacketListener {

    private val BEHOV = BehovType.HENT_IM_ORGNR
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        logger.info("Starter HentOrgnrLøser...")
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.FORESPOERSEL_ID.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        logger.info("Løser behov $BEHOV med forespørselId $forespørselId")
        sikkerLogger.info("Fikk pakke:\n${packet.toPretty()}")
        try {
            val orgnr = repository.hentOrgNr(forespørselId)
            sikkerLogger.info("Fant orgnr: $orgnr for $forespørselId")
            if (orgnr == null) {
                publiserLøsning(HentImOrgnrLøsning(error = Feilmelding("Fant ikke forespørselId $forespørselId")), packet, context)
            } else {
                publiserLøsning(HentImOrgnrLøsning(orgnr), packet, context)
            }
        } catch (ex: Exception) {
            logger.error("Klarte ikke hente persistert orgnr for inntektsmelding for forespørselId $forespørselId")
            sikkerLogger.error("Klarte ikke hente persistert orgnr for inntektsmelding for forespørselId $forespørselId", ex)
            publiserLøsning(HentImOrgnrLøsning(error = Feilmelding("Klarte ikke hente orgnr for persistert inntektsmelding")), packet, context)
        }
    }

    private fun publiserLøsning(løsning: HentImOrgnrLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
