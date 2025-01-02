package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class Melding(
    val fail: Fail,
)

class FeilLytter(
    private val repository: BakgrunnsjobbRepository,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val jobbType = FeilProsessor.JOB_TYPE
    private val eventerSomHaandteres =
        setOf(
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

    override fun les(json: Map<Key, JsonElement>): Melding =
        Melding(
            fail = Key.FAIL.les(Fail.serializer(), json),
        )

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        logger.info("Mottok feil.")
        sikkerLogger.info("Mottok feil.\n${json.toPretty()}")

        if (eventSkalHaandteres(fail.utloesendeMelding)) {
            // slå opp transaksjonID. Hvis den finnes, kan det være en annen feilende melding i samme transaksjon: Lagre i så fall
            // med egen id. Denne id vil så sendes med som ny transaksjonID ved rekjøring.
            val eksisterendeJobb = repository.getById(fail.kontekstId)

            when {
                // Første gang denne flyten feiler
                eksisterendeJobb == null -> {
                    "Lagrer mottatt pakke.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    lagre(
                        Bakgrunnsjobb(
                            uuid = fail.kontekstId,
                            type = jobbType,
                            data = fail.utloesendeMelding.toJson().toString(),
                            maksAntallForsoek = 10,
                        ),
                    )
                }

                // Samme feil har inntruffet flere ganger i samme flyt
                fail.utloesendeMelding == eksisterendeJobb.data.parseJson().toMap() -> {
                    oppdater(eksisterendeJobb)
                }

                // Feil i flyt som tidligere har opplevd annen type feil
                else -> {
                    val nyKontekstId = UUID.randomUUID()
                    val utloesendeMeldingMedNyKontekstId = fail.utloesendeMelding.plus(Key.KONTEKST_ID to nyKontekstId.toJson())

                    "ID '${eksisterendeJobb.uuid}' finnes fra før med annen utløsende melding. Lagrer en ny jobb på ID '$nyKontekstId'.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    lagre(
                        Bakgrunnsjobb(
                            uuid = nyKontekstId,
                            type = jobbType,
                            data = utloesendeMeldingMedNyKontekstId.toJson().toString(),
                            maksAntallForsoek = 10,
                        ),
                    )
                }
            }
        }

        return null
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke håndtere fail.".also {
            logger.error(it)
            sikkerLogger.error(it, error)
        }
        return null
    }

    override fun Melding.loggfelt(): Map<String, String> {
        val eventName = Key.EVENT_NAME.lesOrNull(EventName.serializer(), fail.utloesendeMelding)
        val kontekstId = Key.KONTEKST_ID.lesOrNull(UuidSerializer, fail.utloesendeMelding)
        val behovType = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding)

        val data = fail.utloesendeMelding[Key.DATA]?.toMap().orEmpty()
        val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data)
        val selvbestemtId = Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, data)

        return listOf(
            Log.klasse(this@FeilLytter),
            eventName?.let(Log::event),
            kontekstId?.let(Log::transaksjonId),
            behovType?.let(Log::behov),
            forespoerselId?.let(Log::forespoerselId),
            selvbestemtId?.let(Log::selvbestemtId),
        ).mapNotNull { it }
            .toMap()
    }

    private fun eventSkalHaandteres(utloesendeMelding: Map<Key, JsonElement>): Boolean {
        val eventFraMelding = utloesendeMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())
        val skalHaandteres = eventerSomHaandteres.contains(eventFraMelding)

        "Event '$eventFraMelding' skal håndteres: '$skalHaandteres'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return skalHaandteres
    }

    private fun lagre(jobb: Bakgrunnsjobb) {
        "Lagrer ny jobb med ID '${jobb.uuid}'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }
        repository.save(jobb)
    }

    private fun oppdater(jobb: Bakgrunnsjobb) {
        // Dette må gjøres her fordi jobbene er asynkrone og bakgrunnsjobbService ikke får vite at jobben feiler i disse tilfellene
        // BakgrunnsjobbService finnVentende() tar heller ikke hensyn til forsøk, kun status på jobben!
        jobb.status =
            if (jobb.forsoek > jobb.maksAntallForsoek) {
                "Maks forsøk nådd, stopper jobb med ID '${jobb.uuid}' permanent!".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                BakgrunnsjobbStatus.STOPPET
            } else {
                BakgrunnsjobbStatus.FEILET
            }

        "Oppdaterer eksisterende jobb med ID '${jobb.uuid}'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        repository.update(jobb)
    }
}
