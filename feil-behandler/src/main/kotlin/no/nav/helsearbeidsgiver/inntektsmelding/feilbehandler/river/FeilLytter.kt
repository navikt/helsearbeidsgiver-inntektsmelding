package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
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
        val fail = toFailOrNull(packet.toJson().parseJson())
        if (fail == null) {
            sikkerLogger.warn("Kunne ikke parse feil-objekt, ignorerer...")
        } else if (skalHaandteres(fail)) {
            sikkerLogger.info("Behandler feil")
        } else {
            sikkerLogger.info("Ignorerer feil")
        }
    }

    fun skalHaandteres(fail: Fail): Boolean {
        if (fail.forespoerselId == null) {
            return false
        }
        val behovFraMelding = fail.utloesendeMelding.toMap()[Key.BEHOV]?.fromJson(BehovType.serializer())
        return behovSomHaandteres.contains(behovFraMelding)
    }

    fun toFailOrNull(json: JsonElement): Fail? = // TODO: duplisert, lage felles metode?
        json.toMap()[Key.FAIL]
            ?.runCatching {
                fromJson(Fail.serializer())
            }
            ?.getOrNull()
}
