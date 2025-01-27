package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class TilgangMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val orgnr: Orgnr,
    val fnr: Fnr,
)

class TilgangRiver(
    private val altinnClient: Altinn3M2MClient,
) : ObjectRiver<TilgangMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): TilgangMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            TilgangMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.TILGANGSKONTROLL, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), data),
                fnr = Key.FNR.les(Fnr.serializer(), data),
            )
        }

    override fun TilgangMelding.bestemNoekkel(): KafkaKey = KafkaKey(fnr)

    override fun TilgangMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val harTilgang =
            Metrics.altinnRequest.recordTime(altinnClient::harTilgangTilOrganisasjon) {
                altinnClient.harTilgangTilOrganisasjon(fnr = fnr.verdi, orgnr = orgnr.verdi)
            }

        val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.TILGANG to tilgang.toJson(Tilgang.serializer()),
                    ).toJson(),
        )
    }

    override fun TilgangMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke sjekke tilgang i Altinn.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun TilgangMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@TilgangRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )
}
