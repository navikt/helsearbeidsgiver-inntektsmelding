package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselSakRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FerdigstillForespoerselSakMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID
)

// TODO test
class FerdigstillForespoerselSakRiver(
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val forespoerselSakRepo: ForespoerselSakRepo
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
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json)
            )
        }

    override fun FerdigstillForespoerselSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sakId = forespoerselSakRepo.hentSakId(forespoerselId)
            ?: throw IllegalStateException("Mangler sak-ID for forespurt inntektmelding.")

        return MdcUtils.withLogFields(
            Log.sakId(sakId)
        ) {
            agNotifikasjonKlient.ferdigstillSak(sakId)

            forespoerselSakRepo.lagreSakFerdig(forespoerselId)

            mapOf(
                Key.EVENT_NAME to EventName.SAK_FERDIGSTILT.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SAK_ID to sakId.toJson()
            )
        }
    }

    override fun FerdigstillForespoerselSakMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke ferdigstille/lagre ferdigstilling av sak for forespurt inntektmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FerdigstillForespoerselSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FerdigstillForespoerselSakRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        )
}
