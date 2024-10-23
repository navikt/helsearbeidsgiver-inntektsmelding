package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FerdigstillForespoerselSakMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

class FerdigstillForespoerselSakOgOppgaveRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<FerdigstillForespoerselSakMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): FerdigstillForespoerselSakMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            FerdigstillForespoerselSakMelding(
                eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_BESVART, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            )
        }

    override fun FerdigstillForespoerselSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        ferdigstillSak(forespoerselId)
        ferdigstillOppgave(forespoerselId)

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_FERDIGSTILT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun FerdigstillForespoerselSakMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke ferdigstille sak og/eller oppgave for forespurt inntektmelding.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FerdigstillForespoerselSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FerdigstillForespoerselSakOgOppgaveRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    private fun ferdigstillSak(forespoerselId: UUID) {
        agNotifikasjonKlient
            .ferdigstillSak(
                forespoerselId = forespoerselId,
                nyLenke = NotifikasjonTekst.lenkeFerdigstiltForespoersel(linkUrl, forespoerselId),
            ).onFailure(::loggWarnIkkeFunnet)
    }

    private fun ferdigstillOppgave(forespoerselId: UUID) {
        Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::oppgaveUtfoert) {
            runCatching {
                agNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    nyLenke = NotifikasjonTekst.lenkeFerdigstiltForespoersel(linkUrl, forespoerselId),
                )
            }.recoverCatching {
                agNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                    nyLenke = NotifikasjonTekst.lenkeFerdigstiltForespoersel(linkUrl, forespoerselId),
                )
            }.onFailure(::loggWarnIkkeFunnet)
        }
    }

    private fun loggWarnIkkeFunnet(error: Throwable) {
        if (error is SakEllerOppgaveFinnesIkkeException) {
            logger.warn(error.message)
            sikkerLogger.warn(error.message)
        } else {
            throw error
        }
    }
}
