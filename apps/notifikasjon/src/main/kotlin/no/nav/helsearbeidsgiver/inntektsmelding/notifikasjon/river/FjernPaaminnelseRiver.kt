package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveAlleredeUtfoertException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FjernPaaminnelseMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

class FjernPaaminnelseRiver(
    val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<FjernPaaminnelseMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): FjernPaaminnelseMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            FjernPaaminnelseMelding(
                eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            )
        }

    override fun FjernPaaminnelseMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun FjernPaaminnelseMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        slettOppgavePaaminnelser(forespoerselId = forespoerselId)
        return null
    }

    override fun FjernPaaminnelseMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke fjerne påminnelse fra oppgave.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FjernPaaminnelseMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FjernPaaminnelseRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )

    private fun slettOppgavePaaminnelser(forespoerselId: UUID) {
        runCatching {
            runBlocking {
                agNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    eksternId = forespoerselId.toString(),
                )
            }
        }.onFailure {
            when (it) {
                is SakEllerOppgaveFinnesIkkeException, is OppgaveAlleredeUtfoertException -> {
                    logger.warn(it.message)
                    sikkerLogger.warn(it.message)
                }

                else -> throw it
            }
        }
    }
}
