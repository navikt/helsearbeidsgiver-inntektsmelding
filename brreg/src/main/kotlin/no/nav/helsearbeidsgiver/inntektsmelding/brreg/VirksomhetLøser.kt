@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.publishFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

class VirksomhetLøser(rapidsConnection: RapidsConnection, private val brregClient: BrregClient, private val isPreProd: Boolean) : Løser(rapidsConnection) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.VIRKSOMHET

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

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.requireKey(Key.ORGNRUNDERENHET.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        logger.info("Løser behov $BEHOV med id ${packet[Key.ID.str].asText()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        try {
            val navn = hentVirksomhet(orgnr)
            logger.info("Fant $navn for $orgnr")
            publiserLøsning(VirksomhetLøsning(navn), packet)
            publishDatagram(navn, packet)
        } catch (ex: FantIkkeVirksomhetException) {
            logger.error("Fant ikke virksomhet for $orgnr")
            publiserLøsning(VirksomhetLøsning(error = Feilmelding("Ugyldig virksomhet $orgnr")), packet)
            publishFail(packet.createFail("Ugyldig virksomhet $orgnr", behoveType = BehovType.VIRKSOMHET))
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting for $orgnr")
            sikkerlogg.error("Det oppstod en feil ved henting for orgnr $orgnr: ", ex)
            publiserLøsning(VirksomhetLøsning(error = Feilmelding("Klarte ikke hente virksomhet")), packet)
            publishFail(packet.createFail("Klarte ikke hente virksomhet", behoveType = BehovType.VIRKSOMHET))
        }
    }

    fun publishDatagram(navn: String, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.DATA.str to "",
                Key.UUID.str to jsonMessage[Key.UUID.str].asText(),
                DataFelt.VIRKSOMHET.str to navn
            )
        )
        super.publishData(message)
    }

    fun publiserLøsning(virksomhetLøsning: VirksomhetLøsning, packet: JsonMessage) {
        packet.setLøsning(BEHOV, virksomhetLøsning)
        super.publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
