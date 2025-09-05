package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.toPretty
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class BesvartMelding(
    val notisType: Pri.NotisType,
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val spinnInntektsmeldingId: UUID?,
)

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er besvart. */
class ForespoerselBesvartRiver : ObjectRiver.PriTopic<BesvartMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): BesvartMelding =
        BesvartMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPOERSEL_BESVART, Pri.NotisType.serializer(), json),
            kontekstId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            spinnInntektsmeldingId = Pri.Key.SPINN_INNTEKTSMELDING_ID.lesOrNull(UuidSerializer, json),
        )

    override fun BesvartMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun BesvartMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId?.toJson(),
                ).mapValuesNotNull { it }
                    .toJson(),
        )
    }

    override fun BesvartMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke markere forespørsel som besvart. Arbeidsgiver kan ha åpen sak og oppgave.".also {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error(it, error)
        }

        return null
    }

    override fun BesvartMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselBesvartRiver),
            Log.priNotis(notisType.name),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
