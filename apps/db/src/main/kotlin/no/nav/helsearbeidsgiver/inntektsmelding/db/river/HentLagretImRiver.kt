package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
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
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val forespoerselId: UUID,
)

class HentLagretImRiver(
    private val imRepo: InntektsmeldingRepository,
) : ObjectRiver<HentLagretImMelding>() {
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
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
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
            Key.KONTEKST_ID to transaksjonId.toJson(),
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
                kontekstId = transaksjonId,
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
            Log.transaksjonId(transaksjonId),
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
