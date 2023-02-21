package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import org.slf4j.LoggerFactory

class DistribuerIMLøser(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str,BehovType.SEND_IM_TIL_SPLEIS)
                it.requireKey(Key.ID.str)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.rejectKey(Key.LØSNING.str)
                it.interestedIn(Key.UUID.str)
            }
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

    }


}
