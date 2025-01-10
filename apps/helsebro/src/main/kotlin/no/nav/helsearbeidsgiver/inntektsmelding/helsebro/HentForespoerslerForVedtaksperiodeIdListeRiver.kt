package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class HentForespoerslerForVedtaksperiodeIdListeMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val vedtaksperiodeIdListe: List<UUID>,
)

class HentForespoerslerForVedtaksperiodeIdListeRiver(
    private val priProducer: PriProducer,
) : ObjectRiver<HentForespoerslerForVedtaksperiodeIdListeMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentForespoerslerForVedtaksperiodeIdListeMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentForespoerslerForVedtaksperiodeIdListeMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE, BehovType.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                vedtaksperiodeIdListe = Key.VEDTAKSPERIODE_ID_LISTE.les(UuidSerializer.list(), data),
            )
        }

    // Vi har ingen gode alternativer til Kafka-nøkkel, men det er heller ikke nøye her, så det holder med en tilfeldig verdi
    override fun HentForespoerslerForVedtaksperiodeIdListeMelding.bestemNoekkel(): KafkaKey = KafkaKey(UUID.randomUUID())

    override fun HentForespoerslerForVedtaksperiodeIdListeMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        priProducer
            .send(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                Pri.Key.BOOMERANG to
                    mapOf(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.KONTEKST_ID to transaksjonId.toJson(),
                        Key.DATA to data.toJson(),
                    ).toJson(),
            ).onSuccess {
                logger.info("Publiserte melding på pri-topic om ${Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE}.")
                sikkerLogger.info("Publiserte melding på pri-topic:\n${it.toPretty()}")
            }.onFailure {
                logger.warn("Klarte ikke publiserte melding på pri-topic om ${Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE}.")
                sikkerLogger.warn("Klarte ikke publiserte melding på pri-topic om ${Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE}.")
            }

        return null
    }

    override fun HentForespoerslerForVedtaksperiodeIdListeMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke spørre Storebror om forespørsel for vedtaksperiode-IDer.",
                kontekstId = transaksjonId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentForespoerslerForVedtaksperiodeIdListeMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentForespoerslerForVedtaksperiodeIdListeRiver),
            Log.event(eventName),
            Log.behov(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE),
            Log.transaksjonId(transaksjonId),
        )
}
