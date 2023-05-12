package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser

class PersisterSakLøser(
    rapidsConnection: RapidsConnection,
    val repository: ForespoerselRepository
) : Løser(rapidsConnection) {

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_SAK_ID.name)
            it.requireKey(Key.FORESPOERSEL_ID.str)
            it.requireKey(DataFelt.SAK_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        sikkerLogger.info("PersisterSakLøser mottok pakke: ${packet.toJson()}")
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val sakId = packet[DataFelt.SAK_ID.str].asText()
        repository.oppdaterSakId(sakId, forespoerselId)
        sikkerLogger.info("PersisterSakLøser lagred sakId: $sakId for forespoerselId: $forespoerselId")
        publishData(
            JsonMessage.newMessage(
                mapOf(
                    Key.DATA.str to "",
                    DataFelt.PERSISTERT_SAK_ID.str to sakId,
                    Key.UUID.str to packet[Key.UUID.str]
                )
            )
        )
    }
}
