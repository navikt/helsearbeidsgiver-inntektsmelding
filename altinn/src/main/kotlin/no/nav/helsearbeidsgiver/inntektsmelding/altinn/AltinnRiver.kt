package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
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
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val identitetsnummer: String
)

class AltinnRiver(
    private val altinnClient: AltinnClient
) : ObjectRiver<Melding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (setOf(Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.ARBEIDSGIVERE, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                identitetsnummer = Key.IDENTITETSNUMMER.les(String.serializer(), json)
            )
        }

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val rettigheterForenklet =
            Metrics.altinnRequest.recordTime(altinnClient::hentRettighetOrganisasjoner) {
                altinnClient
                    .hentRettighetOrganisasjoner(identitetsnummer)
                    .mapNotNull { it.orgnr }
                    .toSet()
            }

        val dataField = Key.ORG_RETTIGHETER to rettigheterForenklet.toJson(String.serializer().set())

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to mapOf(dataField).toJson(),
            dataField
        )
    }

    override fun Melding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke hente organisasjonsrettigheter fra Altinn.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@AltinnRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId)
        )
}
