package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.EksternInntektsmelding
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class HentLagretImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val forespoerselId: UUID,
)

class HentLagretImRiver(
    private val imRepo: InntektsmeldingRepository,
) : ObjectRiver.Simba<HentLagretImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentLagretImMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentLagretImMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_LAGRET_IM, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
            )
        }

    override fun HentLagretImMelding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun HentLagretImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val lagret = imRepo.hentNyesteInntektsmelding(forespoerselId)

        loggHentet(lagret)

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.LAGRET_INNTEKTSMELDING to
                            ResultJson(
                                success = lagret?.toJson(LagretInntektsmelding.serializer()),
                            ).toJson(),
                    ).toJson(),
        )
    }

    override fun HentLagretImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente inntektsmelding fra database.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentLagretImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentLagretImRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )

    private fun loggHentet(lagret: LagretInntektsmelding?) {
        when (lagret) {
            is LagretInntektsmelding.Skjema -> {
                logger.info("Fant lagret inntektsmeldingsskjema.")
                sikkerLogger.info("Fant lagret inntektsmeldingsskjema.\n${lagret.skjema.toJson(SkjemaInntektsmelding.serializer()).toPretty()}")
            }
            is LagretInntektsmelding.Ekstern -> {
                logger.info("Fant lagret ekstern inntektsmelding.")
                sikkerLogger.info("Fant lagret ekstern inntektsmelding.\n${lagret.ekstern.toJson(EksternInntektsmelding.serializer()).toPretty()}")
            }
            null -> {
                "Fant _ikke_ lagret inntektsmeldingsskjema eller ekstern inntektsmelding.".also {
                    logger.info(it)
                    sikkerLogger.info(it)
                }
            }
        }
    }
}
