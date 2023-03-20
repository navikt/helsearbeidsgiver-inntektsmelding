package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper

/**
 * Input: FORESPØRSEL_MOTTATT
 *
 * - Hent FulltNavn
 * - Notifikasjon Sak
 * - Persister sak
 * - Notifikasjon Oppgave
 * - Persister oppgave
 *
 */
class ForespørselMottattListener(val rapidsConnection: RapidsConnection) : River.PacketListener {

    private val om = customObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue(Key.EVENT_NAME.str, EventName.FORESPØRSEL_MOTTATT.name)
                it.requireValue(Key.BEHOV.str, BehovType.NOTIFIKASJON_TRENGER_IM.name)
                it.requireKey(Key.ORGNRUNDERENHET.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
                it.requireKey(Key.UUID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("ForespørselMottattListener: Mottok: ${packet.toJson()}")
        val orgnr = packet[Key.ORGNRUNDERENHET.str].asText()
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val msg =
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT.name,
                Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                Key.UUID.str to uuid,
                Key.IDENTITETSNUMMER.str to fnr,
                Key.ORGNRUNDERENHET.str to orgnr
            )

        val json = om.writeValueAsString(msg)
        rapidsConnection.publish(json)
        logger.info("ForespørselMottattListener: publiserte $json")
        //logger.info("ForespørselMottattListener: Ber om FulltNavn for uuid: $uuid")
    }
}
