@file:Suppress("NonAsciiCharacters", "ClassName")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Data
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

class ArbeidsforholdLøser(
    rapidsConnection: RapidsConnection,
    private val aaregClient: AaregClient
) : River.PacketListener {
    private val logger = this.logger()

    private val behovType = BehovType.ARBEIDSFORHOLD

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, behovType)
                it.rejectKey(Key.LØSNING.str)
                it.requireKey(
                    Key.ID.str,
                    Key.IDENTITETSNUMMER.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
        context.publish(packet.toJson())
        val data = if (arbeidsforhold == null) Data(error = Feilmelding("Klarte ikke hente arbeidsforhold")) else Data<Any>(arbeidsforhold)
        publishDatagram(data, packet, context)
    }

    fun puiblishFail(data: Data<Any>, jsonMessage: JsonMessage, context: MessageContext) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.DATA.str to customObjectMapper().writeValueAsString(data),
                Key.UUID.str to jsonMessage[Key.UUID.str].asText()
            )
        )
    }

    fun publishDatagram(data: Data<Any>, jsonMessage: JsonMessage, context: MessageContext) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to jsonMessage[Key.EVENT_NAME.str].asText(),
                Key.DATA.str to customObjectMapper().writeValueAsString(data),
                Key.UUID.str to jsonMessage[Key.UUID.str].asText()
            )
        )
        context.publish(message.toJson())
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
