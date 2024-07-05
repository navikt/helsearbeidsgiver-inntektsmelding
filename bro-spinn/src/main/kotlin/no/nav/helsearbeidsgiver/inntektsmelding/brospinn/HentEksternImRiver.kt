package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class HentEksternImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselId: UUID,
    val spinnImId: UUID
)

class HentEksternImRiver(
    private val spinnKlient: SpinnKlient
) : ObjectRiver<HentEksternImMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentEksternImMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentEksternImMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_EKSTERN_INNTEKTSMELDING, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                data = data,
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                spinnImId = Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, data)
            )
        }

    override fun HentEksternImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Henter ekstern inntektsmelding med ID '$spinnImId' fra Spinn.")

        val eksternInntektsmelding = spinnKlient.hentEksternInntektsmelding(spinnImId)

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to data.plus(
                Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(EksternInntektsmelding.serializer())
            )
                .toJson()
        )
    }

    override fun HentEksternImMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val feilmelding = when (error) {
            is SpinnApiException -> "Klarte ikke hente ekstern inntektsmelding via Spinn API: ${error.message}"
            else -> "Ukjent feil under henting av ekstern inntektsmelding via Spinn API."
        }

        val fail = Fail(
            feilmelding = feilmelding,
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = json.toJson()
        )

        logger.error(feilmelding)
        sikkerLogger.error(feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentEksternImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentEksternImRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        )
}
