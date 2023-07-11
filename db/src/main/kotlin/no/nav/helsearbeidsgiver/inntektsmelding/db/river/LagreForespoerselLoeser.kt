package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class LagreForespoerselLoeser(rapidsConnection: RapidsConnection, private val repository: ForespoerselRepository) : Løser(rapidsConnection) {

    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_FORESPOERSEL.name)
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        sikkerLogger.info("LagreForespoerselLoeser mottok:\n${packet.toPretty()}")
        val orgnr = packet[DataFelt.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        repository.lagreForespoersel(forespoerselId, orgnr)

        val msg =
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                    Key.IDENTITETSNUMMER.str to fnr,
                    DataFelt.ORGNRUNDERENHET.str to orgnr
                )
            )

        publishEvent(msg)
        sikkerLogger.info("LagreForespoerselLoeser publiserte:\n${msg.toPretty()}")
    }
}
