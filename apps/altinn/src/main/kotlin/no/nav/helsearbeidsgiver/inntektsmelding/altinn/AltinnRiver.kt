package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
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
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val fnr: Fnr,
)

class AltinnRiver(
    private val altinnClient: Altinn3M2MClient,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.ARBEIDSGIVERE, BehovType.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                fnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data),
            )
        }

    override fun Melding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val rettigheterForenklet =
            Metrics.altinnRequest.recordTime(altinnClient::hentTilganger) {
                altinnClient.hentTilganger(fnr.verdi)
            }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.ORG_RETTIGHETER to rettigheterForenklet.toJson(String.serializer().set()),
                    ).toJson(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente organisasjonsrettigheter fra Altinn.",
                kontekstId = transaksjonId,
                utloesendeMelding = json,
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
            Log.transaksjonId(transaksjonId),
        )
}
