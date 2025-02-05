package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class ForespoerselSvarMelding(
    val eventName: EventName,
    val behovType: Pri.BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselSvar: ForespoerselSvar,
)

class ForespoerselSvarRiver : PriObjectRiver<ForespoerselSvarMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): ForespoerselSvarMelding {
        val forespoerselSvar = Pri.Key.LOESNING.les(ForespoerselSvar.serializer(), json)
        val boomerang = forespoerselSvar.boomerang.toMap()

        return ForespoerselSvarMelding(
            eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerang),
            behovType = Pri.Key.BEHOV.krev(Pri.BehovType.TRENGER_FORESPØRSEL, Pri.BehovType.serializer(), json),
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, boomerang),
            data = boomerang[Key.DATA]?.toMap().orEmpty(),
            forespoerselSvar = forespoerselSvar,
        )
    }

    override fun ForespoerselSvarMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselSvar.forespoerselId)

    override fun ForespoerselSvarMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok løsning på pri-topic om $behovType.")
        sikkerLogger.info("Mottok løsning på pri-topic:\n$json")

        return if (forespoerselSvar.resultat != null) {
            val forespoersel = forespoerselSvar.resultat.toForespoersel()

            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.FORESPOERSEL_ID to forespoerselSvar.forespoerselId.toJson(),
                                Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
                            ),
                        ).toJson(),
            )
        } else {
            throw ForespoerselManglerException()
        }
    }

    override fun ForespoerselSvarMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val feilmelding =
            when (error) {
                is ForespoerselManglerException -> {
                    if (forespoerselSvar.feil != null) {
                        "Klarte ikke hente forespørsel. Feilet med kode '${forespoerselSvar.feil}'."
                    } else {
                        "Svar fra bro-appen har hverken resultat eller feil."
                    }
                }

                else -> {
                    "Klarte ikke hente forespørsel. Ukjent feil."
                }
            }

        val fail =
            Fail(
                feilmelding = feilmelding,
                kontekstId = kontekstId,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                        Key.KONTEKST_ID to kontekstId.toJson(),
                        Key.DATA to data.toJson(),
                    ),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun ForespoerselSvarMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselSvarRiver),
            Log.event(eventName),
            Log.behov(BehovType.HENT_TRENGER_IM),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselSvar.forespoerselId),
        )
}

private class ForespoerselManglerException : RuntimeException()
