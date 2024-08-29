package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.sql.SQLException
import java.util.UUID

class FeilLytter(
    rapidsConnection: RapidsConnection,
    private val repository: BakgrunnsjobbRepository,
) : River.PacketListener {
    private val jobbType = FeilProsessor.JOB_TYPE

    private val sikkerLogger = sikkerLogger()
    val behovSomHaandteres =
        listOf(
            BehovType.LAGRE_FORESPOERSEL,
            BehovType.OPPRETT_OPPGAVE,
            BehovType.OPPRETT_SAK,
            BehovType.PERSISTER_OPPGAVE_ID,
            BehovType.PERSISTER_SAK_ID,
            BehovType.JOURNALFOER,
            BehovType.LAGRE_JOURNALPOST_ID,
            BehovType.NOTIFIKASJON_HENT_ID,
        )
    val eventerSomHaandteres =
        listOf(
            EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
        )

    init {
        sikkerLogger.info("Starter applikasjon - lytter på innkommende feil!")
        River(rapidsConnection)
            .apply {
                validate { msg ->
                    msg.demandKey(Key.FAIL.str)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        sikkerLogger.info("Mottok feil: ${packet.toPretty()}")
        val fail = toFailOrNull(packet.toJson().parseJson().toMap())
        if (fail == null) {
            sikkerLogger.warn("Kunne ikke parse feil-objekt, ignorerer...")
            return
        }
        if (behovSkalHaandteres(fail) || eventSkalHaandteres(fail)) {
            // slå opp transaksjonID. Hvis den finnes, kan det være en annen feilende melding i samme transaksjon (forskjelig behov): Lagre i så fall
            // med egen id. Denne id vil så sendes med som ny transaksjonID ved rekjøring..
            val jobbId = fail.transaksjonId
            val eksisterendeJobb = repository.getById(jobbId)
            if (eksisterendeJobb != null) {
                if (fail.utloesendeMelding.toMap()[Key.BEHOV] != eksisterendeJobb.data.parseJson().toMap()[Key.BEHOV]) {
                    sikkerLogger.info("Id $jobbId finnes fra før med annet behov. Lagrer en ny jobb.")
                    val nyTransaksjonId = UUID.randomUUID()
                    val utloesendeMeldingMedNyTransaksjonId = fail.utloesendeMelding.toMap() + mapOf(Key.UUID to nyTransaksjonId.toJson(UuidSerializer))
                    lagre(
                        Bakgrunnsjobb(nyTransaksjonId, type = jobbType, data = utloesendeMeldingMedNyTransaksjonId.toJson().toString(), maksAntallForsoek = 10),
                    )
                } else {
                    oppdater(eksisterendeJobb)
                }
            } else {
                sikkerLogger.info("Lagrer mottatt pakke!")
                val jobb =
                    Bakgrunnsjobb(
                        uuid = fail.transaksjonId,
                        type = jobbType,
                        data = fail.utloesendeMelding.toString(),
                        maksAntallForsoek = 10,
                    )
                lagre(jobb)
            }
        }
    }

    private fun oppdater(jobb: Bakgrunnsjobb) {
        // Dette må gjøres her fordi jobbene er asynkrone og bakgrunnsjobbService ikke får vite at jobben feiler i disse tilfellene
        // BakgrunnsjobbService finnVentende() tar heller ikke hensyn til forsøk, kun status på jobben!
        if (jobb.forsoek > jobb.maksAntallForsoek) {
            jobb.status = BakgrunnsjobbStatus.STOPPET
            sikkerLogger.warn("Maks forsøk nådd, stopper jobb med id ${jobb.uuid} permanent!")
        } else {
            jobb.status = BakgrunnsjobbStatus.FEILET
        }
        try {
            repository.update(jobb)
            sikkerLogger.info("Oppdaterte eksisterende jobb med id ${jobb.uuid}")
        } catch (ex: SQLException) {
            sikkerLogger.error("Oppdatering av jobb med id ${jobb.uuid} feilet!", ex)
        }
    }

    private fun lagre(jobb: Bakgrunnsjobb) {
        try {
            repository.save(jobb)
            sikkerLogger.info("Lagret ny jobb med id ${jobb.uuid}")
        } catch (ex: SQLException) {
            sikkerLogger.error("Lagring av jobb med id ${jobb.uuid} feilet!", ex)
        }
    }

    fun behovSkalHaandteres(fail: Fail): Boolean {
        val behovFraMelding = fail.utloesendeMelding.toMap()[Key.BEHOV]?.fromJson(BehovType.serializer())
        val skalHaandteres = behovSomHaandteres.contains(behovFraMelding)
        sikkerLogger.info("Behov: $behovFraMelding skal håndteres: $skalHaandteres")
        return skalHaandteres
    }

    fun eventSkalHaandteres(fail: Fail): Boolean {
        val eventFraMelding = fail.utloesendeMelding.toMap()[Key.EVENT_NAME]?.fromJson(EventName.serializer())
        val skalHaandteres = eventerSomHaandteres.contains(eventFraMelding)
        sikkerLogger.info("Event: $eventFraMelding skal håndteres: $skalHaandteres")
        return skalHaandteres
    }
}
