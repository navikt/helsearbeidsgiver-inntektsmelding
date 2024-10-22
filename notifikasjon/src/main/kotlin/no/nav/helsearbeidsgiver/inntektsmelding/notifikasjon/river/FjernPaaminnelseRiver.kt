package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FjernPaaminnelseMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

class FjernPaaminnelseRiver(
    agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<FjernPaaminnelseMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun FjernPaaminnelseMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        runCatching {
            "FjernPaaminnelseRiver skulle her ha fjernet påminnelsen på oppgaven, men det er ikke implementert ennå.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        }.onFailure {
            if (it is SakEllerOppgaveFinnesIkkeException) {
                logger.warn(it.message)
                sikkerLogger.warn(it.message)
            } else {
                throw it
            }
        }

        // TODO: Send melding når påminnelsen er fjernet
        return null
    }

    override fun les(json: Map<Key, JsonElement>): FjernPaaminnelseMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            FjernPaaminnelseMelding(
                eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            )
        }

    override fun FjernPaaminnelseMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FjernPaaminnelseRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    override fun FjernPaaminnelseMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke fjerne påminnelse fra oppgave.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }
}
