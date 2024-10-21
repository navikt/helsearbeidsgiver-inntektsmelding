package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselinfotrygd

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class KastetTilInfotrygdMelding(
    val notisType: Pri.NotisType,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

/** Tar imot notis om at en forespørsel om arbeidsgiveropplysninger er kastet til Infotrygd. */
class ForespoerselKastetTilInfotrygdRiver : PriObjectRiver<KastetTilInfotrygdMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): KastetTilInfotrygdMelding =
        KastetTilInfotrygdMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD, Pri.NotisType.serializer(), json),
            transaksjonId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
        )

    override fun KastetTilInfotrygdMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun KastetTilInfotrygdMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke videresendre beskjed om forespørsel kastet til Infotrygd. Arbeidsgiver kan motta purring selv om de har sendt inn IM gjennom Altinn."
            .also {
                logger.error("$it Se sikker logg for mer info.")
                sikkerLogger.error(it, error)
            }

        return null
    }

    override fun KastetTilInfotrygdMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselKastetTilInfotrygdRiver),
            Log.priNotis(notisType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
