@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatelessLøser
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.system.measureTimeMillis

class IkkeBlokkerendeVirksomhetLøser(
    rapidsConnection: RapidsConnection,
    private val brregClient: BrregClient,
    private val isPreProd: Boolean,
    val delayMs: Long = 0
) : StatelessLøser(rapidsConnection) {

    private val logger = logger()
    private val BEHOV = BehovType.VIRKSOMHET

    private suspend fun hentVirksomhet(orgnr: String): String {
        if (isPreProd) {
            if (delayMs > 0) {
                logger.warn("Kjører i testmodus, forsinker kallet med $delayMs millisekunder")
            }
            delay(delayMs)
            return when (orgnr) {
                "810007702" -> "ANSTENDIG PIGGSVIN BYDEL"
                "810007842" -> "ANSTENDIG PIGGSVIN BARNEHAGE"
                "810008032" -> "ANSTENDIG PIGGSVIN BRANNVESEN"
                "810007982" -> "ANSTENDIG PIGGSVIN SYKEHJEM"
                else -> {"Ukjent arbeidsgiver"}
            }
        }
        val virksomhetNav: String?
        measureTimeMillis {
            virksomhetNav = brregClient.hentVirksomhetNavn(orgnr)
        }.also {
            logger.info("BREG execution took $it")
        }
        return virksomhetNav ?: throw FantIkkeVirksomhetException(orgnr)
    }

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.requireKey(Key.ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        logger.info("Løser behov $BEHOV med id ${packet[Key.ID.str].asText()}")
        val orgnr = packet[DataFelt.ORGNRUNDERENHET.str].asText()
        hentNavnIkkeBlokker(orgnr, packet)
        logger.info("Jeg er ferdig med onBehov...")
    }

    fun hentNavnIkkeBlokker(orgnr: String, packet: JsonMessage) {
        logger.info("Kaller brreg")
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val navn = hentVirksomhet(orgnr)
                logger.info("Fant $navn for $orgnr")
                publiserLøsning(VirksomhetLøsning(navn), packet)
                publishDatagram(navn, packet)
            } catch (ex: FantIkkeVirksomhetException) {
                logger.error("Fant ikke virksomhet for $orgnr")
                publiserLøsning(VirksomhetLøsning(error = Feilmelding("Ugyldig virksomhet $orgnr")), packet)
                publishFail(packet.createFail("Ugyldig virksomhet $orgnr", behovType = BehovType.VIRKSOMHET))
            } catch (ex: Exception) {
                logger.error("Det oppstod en feil ved henting for $orgnr")
                sikkerLogger.error("Det oppstod en feil ved henting for orgnr $orgnr: ", ex)
                publiserLøsning(VirksomhetLøsning(error = Feilmelding("Klarte ikke hente virksomhet")), packet)
                publishFail(packet.createFail("Klarte ikke hente virksomhet", behovType = BehovType.VIRKSOMHET))
            }
        }
    }
    private fun publishDatagram(navn: String, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.FORESPOERSEL_ID.str to jsonMessage[Key.FORESPOERSEL_ID.str].asText(),
                Key.DATA.str to "",
                Key.UUID.str to jsonMessage[Key.UUID.str].asText(),
                DataFelt.VIRKSOMHET.str to navn
            )
        )
        super.publishData(message)
    }

    private fun publiserLøsning(virksomhetLøsning: VirksomhetLøsning, packet: JsonMessage) {
        packet.setLøsning(BEHOV, virksomhetLøsning)
        super.publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
