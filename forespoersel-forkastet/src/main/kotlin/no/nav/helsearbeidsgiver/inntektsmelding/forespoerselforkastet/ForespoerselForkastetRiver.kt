package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselforkastet

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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

data class BesvartSpleisMelding(
    val notisType: Pri.NotisType,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er forkastet. */
class ForespoerselForkastetRiver(
    private val rapid: RapidsConnection,
) : PriObjectRiver<BesvartSpleisMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): BesvartSpleisMelding =
        BesvartSpleisMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPOERSEL_FORKASTET, Pri.NotisType.serializer(), json),
            transaksjonId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
        )

    override fun BesvartSpleisMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        // Metrics.forespoerslerBesvartFraSpleis.inc()

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun BesvartSpleisMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke markere forespørsel som forkastet. Arbeidsgiver kan ha åpen sak og oppgave.".also {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error(it, error)
        }

        return null
    }

    override fun BesvartSpleisMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselForkastetRiver),
            Log.priNotis(notisType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
