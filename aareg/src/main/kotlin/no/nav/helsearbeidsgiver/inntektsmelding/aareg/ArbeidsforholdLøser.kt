@file:Suppress("NonAsciiCharacters", "ClassName")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Data
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.publishFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

class ArbeidsforholdLøser(
    rapidsConnection: RapidsConnection,
    private val aaregClient: AaregClient
) : Løser(rapidsConnection) {
    private val logger = this.logger()

    private val behovType = BehovType.ARBEIDSFORHOLD

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, behovType)
            it.requireKey(
                Key.ID.str,
                Key.IDENTITETSNUMMER.str
            )
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val id = packet.value(Key.ID).asText()
        val identitetsnummer = packet.value(Key.IDENTITETSNUMMER).asText()

        logger.info("Løser behov $behovType med id $id")

        val arbeidsforhold = hentArbeidsforhold(identitetsnummer, id)

        val løsning = if (arbeidsforhold != null) {
            ArbeidsforholdLøsning(arbeidsforhold)
        } else {
            ArbeidsforholdLøsning(error = Feilmelding("Klarte ikke hente arbeidsforhold"))
        }

        packet.setLøsning(behovType, løsning)
        super.publishBehov(packet)

        if (arbeidsforhold != null) {
            publishDatagram(Data(arbeidsforhold), packet)
        } else {
            publishFail(packet.createFail("Klarte ikke hente arbeidsforhold", behoveType = BehovType.ARBEIDSFORHOLD))
        }
    }

    fun publishDatagram(data: Data<Any>, jsonMessage: JsonMessage) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.DATA.str to "",
                Key.UUID.str to jsonMessage[Key.UUID.str].asText(),
                DataFelt.ARBEIDSFORHOLD.str to data
            )
        )
        publishData(message)
    }

    private fun hentArbeidsforhold(fnr: String, callId: String): List<Arbeidsforhold>? =
        runCatching {
            runBlocking {
                aaregClient.hentArbeidsforhold(fnr, callId)
            }
        }
            .onFailure {
                sikkerlogg.error("Det oppstod en feil ved henting av arbeidsforhold for $fnr", it)
            }
            .getOrNull()
            ?.map(KlientArbeidsforhold::tilArbeidsforhold)
            ?.also {
                sikkerlogg.info("Fant arbeidsforhold $it for $fnr")
            }
}

private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
    this[Key.LØSNING.str] = mapOf(
        nøkkel.name to data
    )
}
