package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

class PersisterSakLøser(
    rapidsConnection: RapidsConnection,
    val repository: Repository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, listOf(BehovType.PERSISTER_SAK_ID.name))
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.SAK_ID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("PersisterSakLøser mottok pakke: ${packet.toJson()}")
        val uuid = packet[Key.UUID.str].asText()
        val sakId = packet[Key.SAK_ID.str].asText()
        repository.oppdaterSakId(sakId, uuid)
        sikkerLogger.info("PersisterSakLøser lagred sakId: $sakId for uuid: $uuid")
    }
}
