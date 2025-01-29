package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convertAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convertInntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
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
    val kontekstId: UUID,
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
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
            )
        }

    override fun HentLagretImMelding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun HentLagretImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val (
            skjema,
            inntektsmelding,
            eksternInntektsmelding,
        ) =
            imRepo
                .hentNyesteEksternEllerInternInntektsmelding(forespoerselId)
                .let { (skjema, inntektsmelding, eksternInntektsmelding) ->
                    val bakoverkompatibeltSkjema =
                        skjema ?: inntektsmelding?.let {
                            SkjemaInntektsmelding(
                                forespoerselId = forespoerselId,
                                avsenderTlf = it.telefonnummer.orEmpty(),
                                agp = it.convertAgp(),
                                inntekt = it.convertInntekt(),
                                refusjon = it.refusjon.convert(),
                            )
                        }

                    Triple(bakoverkompatibeltSkjema, inntektsmelding, eksternInntektsmelding)
                }.tilPayloadTriple()

        loggHentet(inntektsmelding, eksternInntektsmelding)

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(),
                            Key.LAGRET_INNTEKTSMELDING to inntektsmelding.toJson(),
                            Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(),
                        ),
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

    private fun loggHentet(
        inntektsmelding: ResultJson,
        eksternInntektsmelding: ResultJson,
    ) {
        if (inntektsmelding.success == null) {
            "Fant _ikke_ lagret inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            logger.info("Fant lagret inntektsmelding.")
            sikkerLogger.info("Fant lagret inntektsmelding.\n${inntektsmelding.success?.toPretty()}")
        }

        if (eksternInntektsmelding.success == null) {
            "Fant _ikke_ lagret ekstern inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            logger.info("Fant lagret ekstern inntektsmelding.")
            sikkerLogger.info("Fant lagret ekstern inntektsmelding.\n${eksternInntektsmelding.success?.toPretty()}")
        }
    }
}

private fun Triple<SkjemaInntektsmelding?, Inntektsmelding?, EksternInntektsmelding?>.tilPayloadTriple(): Triple<ResultJson, ResultJson, ResultJson> =
    Triple(
        first?.toJson(SkjemaInntektsmelding.serializer()).toSuccess(),
        second?.toJson(Inntektsmelding.serializer()).toSuccess(),
        third?.toJson(EksternInntektsmelding.serializer()).toSuccess(),
    )

private fun JsonElement?.toSuccess(): ResultJson = ResultJson(success = this)
