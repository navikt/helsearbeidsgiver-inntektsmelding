package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val AVSENDER_NAV_NO = "NAV_NO"
private const val AVSENDER_NAV_NO_SELVBESTEMT = "NAV_NO_SELVBESTEMT"

class HentEksternImMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val spinnImId: UUID,
)

class HentEksternImRiver(
    private val spinnKlient: SpinnKlient,
) : ObjectRiver<HentEksternImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentEksternImMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentEksternImMelding(
                eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_BESVART, EventName.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                spinnImId = Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, data),
            )
        }

    override fun HentEksternImMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun HentEksternImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        logger.info("Henter ekstern inntektsmelding med ID '$spinnImId' fra Spinn.")

        val eksternInntektsmelding = spinnKlient.hentEksternInntektsmelding(spinnImId)

        return if (eksternInntektsmelding.avsenderSystemNavn in setOf(AVSENDER_NAV_NO, AVSENDER_NAV_NO_SELVBESTEMT)) {
            null
        } else {
            mapOf(
                Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                Key.KONTEKST_ID to transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(EksternInntektsmelding.serializer()),
                    ).toJson(),
            )
        }
    }

    override fun HentEksternImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val feilmelding =
            when (error) {
                is SpinnApiException -> "Klarte ikke hente ekstern inntektsmelding via Spinn API: ${error.message}"
                else -> "Ukjent feil under henting av ekstern inntektsmelding via Spinn API."
            }

        val fail =
            Fail(
                feilmelding = feilmelding,
                kontekstId = transaksjonId,
                utloesendeMelding = json,
            )

        logger.error(feilmelding)
        sikkerLogger.error(feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentEksternImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentEksternImRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
