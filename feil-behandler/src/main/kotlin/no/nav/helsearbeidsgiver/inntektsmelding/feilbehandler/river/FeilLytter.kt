package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import kotlinx.serialization.serializer
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException

class FeilLytter(rapidsConnection: RapidsConnection, private val repository: BakgrunnsjobbRepository) : River.PacketListener {

    private val jobbType = "kafka-retry-message"

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
        if (fail == null) {
            sikkerLogger.warn("Kunne ikke parse feil-objekt, ignorerer...")
            return
        }
        if (skalHaandteres(fail)) {
            // slå opp transaksjonID. Hvis den finnes, kan det være en annen feilende melding i samme transaksjon (forskjelig behov): Lagre i så fall
            // med egen UUID. Denne UUID vil så sendes med som ny transaksjonID ved rekjøring..
            val jobbId = fail.transaksjonId
            val eksisterendeJobb = repository.getById(jobbId)
            if (eksisterendeJobb != null &&
                (fail.utloesendeMelding.toMap()[Key.BEHOV] != eksisterendeJobb.data.parseJson().toMap()[Key.BEHOV])
            ) {
                sikkerLogger.info("Id $jobbId finnes fra før med annet behov. Lagrer en ny jobb.")
                repository.save(Bakgrunnsjobb(type = jobbType, data = fail.utloesendeMelding.toString()))
            } else {
                sikkerLogger.info("Lagrer mottatt pakke!")
                val jobb = Bakgrunnsjobb(
                    uuid = fail.transaksjonId,
                    type = jobbType,
                    data = fail.utloesendeMelding.toString()
                )
                lagreEllerOppdater(jobb)
            }
        }
    }

    private fun lagreEllerOppdater(jobb: Bakgrunnsjobb) {
        try {
            repository.save(jobb)
            sikkerLogger.info("Lagret ny jobb med id ${jobb.uuid}")
        } catch (e: SQLIntegrityConstraintViolationException) {
            jobb.forsoek = jobb.forsoek + 1
            repository.update(jobb)
            sikkerLogger.info("Oppdaterte eksisterende jobb med id ${jobb.uuid}")
        } catch (ex: SQLException) {
            sikkerLogger.error("Lagring av jobb med id ${jobb.uuid} feilet!", ex)
        }
    }

    fun skalHaandteres(fail: Fail): Boolean {
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
