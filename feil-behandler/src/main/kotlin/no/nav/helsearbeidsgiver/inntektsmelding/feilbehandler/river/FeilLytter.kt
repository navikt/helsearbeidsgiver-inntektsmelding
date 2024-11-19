package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.sql.SQLException
import java.util.UUID

class FeilLytter(
    rapidsConnection: RapidsConnection,
    private val repository: BakgrunnsjobbRepository,
) : River.PacketListener {
    private val jobbType = FeilProsessor.JOB_TYPE

    private val sikkerLogger = sikkerLogger()

    private val eventerSomHaandteres =
        listOf(
            EventName.FORESPOERSEL_MOTTATT,
            EventName.FORESPOERSEL_BESVART,
            EventName.FORESPOERSEL_FORKASTET,
            EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD,
            EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED,
            EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
            EventName.INNTEKTSMELDING_MOTTATT,
            EventName.INNTEKTSMELDING_JOURNALFOERT,
            EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET,
            EventName.SELVBESTEMT_IM_LAGRET,
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
        sikkerLogger.info("Mottok feil: ${packet.toJson().parseJson().toPretty()}")
        val fail =
            packet
                .toJson()
                .parseJson()
                .toMap()[Key.FAIL]
                ?.runCatching {
                    fromJson(Fail.serializer())
                }?.getOrNull()

        if (fail == null) {
            sikkerLogger.warn("Kunne ikke parse feil-objekt, ignorerer...")
            return
        }

        val utloesendeMelding = fail.utloesendeMelding.toMap()
        if (eventSkalHaandteres(utloesendeMelding)) {
            // slå opp transaksjonID. Hvis den finnes, kan det være en annen feilende melding i samme transaksjon: Lagre i så fall
            // med egen id. Denne id vil så sendes med som ny transaksjonID ved rekjøring.
            val jobbId = fail.transaksjonId
            val eksisterendeJobb = repository.getById(jobbId)

            when {
                // Første gang denne flyten feiler
                eksisterendeJobb == null -> {
                    sikkerLogger.info("Lagrer mottatt pakke!")
                    lagre(
                        Bakgrunnsjobb(
                            uuid = fail.transaksjonId,
                            type = jobbType,
                            data = fail.utloesendeMelding.toString(),
                            maksAntallForsoek = 10,
                        ),
                    )
                }

                // Samme feil har inntruffet flere ganger i samme flyt
                utloesendeMelding == eksisterendeJobb.data.parseJson().toMap() -> {
                    oppdater(eksisterendeJobb)
                }

                // Feil i flyt som tidligere har opplevd annen type feil
                else -> {
                    val nyTransaksjonId = UUID.randomUUID()
                    val utloesendeMeldingMedNyTransaksjonId = utloesendeMelding.plus(Key.KONTEKST_ID to nyTransaksjonId.toJson())

                    sikkerLogger.info("ID $jobbId finnes fra før med annen utløsende melding. Lagrer en ny jobb på ID '$nyTransaksjonId'.")

                    lagre(
                        Bakgrunnsjobb(
                            uuid = nyTransaksjonId,
                            type = jobbType,
                            data = utloesendeMeldingMedNyTransaksjonId.toJson().toString(),
                            maksAntallForsoek = 10,
                        ),
                    )
                }
            }
        }
    }

    private fun oppdater(jobb: Bakgrunnsjobb) {
        // Dette må gjøres her fordi jobbene er asynkrone og bakgrunnsjobbService ikke får vite at jobben feiler i disse tilfellene
        // BakgrunnsjobbService finnVentende() tar heller ikke hensyn til forsøk, kun status på jobben!
        if (jobb.forsoek > jobb.maksAntallForsoek) {
            jobb.status = BakgrunnsjobbStatus.STOPPET
            sikkerLogger.error("Maks forsøk nådd, stopper jobb med id ${jobb.uuid} permanent!")
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

    private fun eventSkalHaandteres(utloesendeMelding: Map<Key, JsonElement>): Boolean {
        val eventFraMelding = utloesendeMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())
        val skalHaandteres = eventerSomHaandteres.contains(eventFraMelding)
        sikkerLogger.info("Event: $eventFraMelding skal håndteres: $skalHaandteres")
        return skalHaandteres
    }
}
