package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import kotlinx.serialization.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.Companion.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class FeilLytter(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val sikkerLogger = sikkerLogger()
    val behovSomHaandteres = listOf(
        BehovType.LAGRE_FORESPOERSEL,
        BehovType.OPPRETT_OPPGAVE,
        BehovType.OPPRETT_SAK,
        BehovType.PERSISTER_OPPGAVE_ID,
        BehovType.PERSISTER_SAK_ID,
        BehovType.JOURNALFOER,
        BehovType.LAGRE_JOURNALPOST_ID,
        BehovType.NOTIFIKASJON_HENT_ID
    )

    init {
        sikkerLogger.info("Starter applikasjon - lytter på innkommende feil!")
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demandKey(Key.FAIL.str)
            }
        }
            .register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.info("Mottok feil: ${packet.toPretty()}")
        val fail = toFailOrNull(packet.toJson().parseJson().toMap())
        if (skalHaandteres(fail)) {
            sikkerLogger.info("Behandler mottatt pakke")
        }
    }

    fun skalHaandteres(fail: Fail?): Boolean {
        if (fail == null) {
            sikkerLogger.warn("Kunne ikke parse feil-objekt, ignorerer...")
            return false
        }
        if (fail.forespoerselId == null) {
            sikkerLogger.info("Mangler forespørselId, ignorerer")
            return false
        }
        val behovFraMelding = fail.utloesendeMelding.toMap()[Key.BEHOV]?.fromJson(BehovType.serializer())
        val skalHaandteres = behovSomHaandteres.contains(behovFraMelding)
        sikkerLogger.info("Behov: $behovFraMelding skal håndteres: $skalHaandteres")
        return skalHaandteres
    }
}
