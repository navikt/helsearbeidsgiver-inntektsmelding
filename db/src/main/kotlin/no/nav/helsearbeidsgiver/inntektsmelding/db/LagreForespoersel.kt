package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser

class LagreForespoersel(rapidsConnection: RapidsConnection, val repository: ForespoerselRepository) : Løser(rapidsConnection) {

    private val om = customObjectMapper()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val forespørselId = packet[Key.FORESPOERSEL_ID.str].asText()
        sikkerLogger.info("LagreForespoersel mottok: ${packet.toJson()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        repository.lagreForespørsel(forespørselId, orgnr)

        val msg =
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                    Key.IDENTITETSNUMMER.str to fnr,
                    Key.ORGNRUNDERENHET.str to orgnr
                )
            )

        publishEvent(msg)
        sikkerLogger.info("LagreForespoersel publiserte: ${msg.toJson()}")
    }
}
