package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillOppgave
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FerdigstillForespoerselSakMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

class FerdigstillForespoerselSakOgOppgaveRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver.Simba<FerdigstillForespoerselSakMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): FerdigstillForespoerselSakMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), json)

            // Støtter både inntektmelding mottatt av Simba og event fra Storebror
            if (eventName !in setOf(EventName.INNTEKTSMELDING_MOTTATT, EventName.FORESPOERSEL_BESVART)) {
                null
            } else {
                FerdigstillForespoerselSakMelding(
                    eventName = eventName,
                    kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                    forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, json) ?: Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                )
            }
        }

    override fun FerdigstillForespoerselSakMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun FerdigstillForespoerselSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val lenke = NotifikasjonTekst.lenkeFerdigstiltForespoersel(linkUrl, forespoerselId)

        agNotifikasjonKlient.ferdigstillSak(lenke, forespoerselId)
        agNotifikasjonKlient.ferdigstillOppgave(lenke, forespoerselId)

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_FERDIGSTILT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
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
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FerdigstillForespoerselSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FerdigstillForespoerselSakOgOppgaveRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
