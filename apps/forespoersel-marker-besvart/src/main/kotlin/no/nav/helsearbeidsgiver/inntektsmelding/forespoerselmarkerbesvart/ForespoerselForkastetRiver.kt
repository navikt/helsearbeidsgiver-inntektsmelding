package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.toPretty
import no.nav.helsearbeidsgiver.felles.rr.KafkaKey
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class ForkastetMelding(
    val notisType: Pri.NotisType,
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er forkastet. */
class ForespoerselForkastetRiver : ObjectRiver.PriTopic<ForkastetMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): ForkastetMelding =
        ForkastetMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPOERSEL_FORKASTET, Pri.NotisType.serializer(), json),
            kontekstId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
        )

    override fun ForkastetMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun ForkastetMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_FORKASTET}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        // lag ny metrikk for forespørsler forkastet fra spleis
        // Metrics.forespoerslerBesvartFraSpleis.inc()

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun ForkastetMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke videresende beskjed om forkastet forespørsel. Arbeidsgiver kan ha åpen sak og oppgave.".also {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error(it, error)
        }

        return null
    }

    override fun ForkastetMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselForkastetRiver),
            Log.priNotis(notisType),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
