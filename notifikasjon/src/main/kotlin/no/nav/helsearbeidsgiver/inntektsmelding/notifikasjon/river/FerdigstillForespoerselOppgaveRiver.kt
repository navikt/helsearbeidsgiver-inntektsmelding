package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselOppgaveRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FerdigstillForespoerselOppgaveMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID
)

// TODO test
class FerdigstillForespoerselOppgaveRiver(
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val forespoerselOppgaveRepo: ForespoerselOppgaveRepo
) : ObjectRiver<FerdigstillForespoerselOppgaveMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): FerdigstillForespoerselOppgaveMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            FerdigstillForespoerselOppgaveMelding(
                eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_BESVART, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json)
            )
        }

    override fun FerdigstillForespoerselOppgaveMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val oppgaveId = forespoerselOppgaveRepo.hentOppgaveId(forespoerselId)
            ?: throw IllegalStateException("Mangler oppgave-ID for forespurt inntektmelding.")

        return MdcUtils.withLogFields(
            Log.oppgaveId(oppgaveId)
        ) {
            Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::oppgaveUtfoert) {
                agNotifikasjonKlient.oppgaveUtfoert(oppgaveId)

                forespoerselOppgaveRepo.lagreOppgaveFerdig(forespoerselId)

                mapOf(
                    Key.EVENT_NAME to EventName.OPPGAVE_FERDIGSTILT.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.OPPGAVE_ID to oppgaveId.toJson()
                )
            }
        }
    }

    override fun FerdigstillForespoerselOppgaveMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke ferdigstille/lagre ferdigstilling av oppgave for forespurt inntektmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FerdigstillForespoerselOppgaveMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FerdigstillForespoerselOppgaveRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        )
}
