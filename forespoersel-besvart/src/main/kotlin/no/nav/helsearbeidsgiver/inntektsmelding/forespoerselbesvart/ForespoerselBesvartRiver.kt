package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class BesvartMelding(
    val notisType: Pri.NotisType,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val spinnInntektsmeldingId: UUID?,
)

/** Tar imot notifikasjon om at en forespørsel om arbeidsgiveropplysninger er besvart. */
class ForespoerselBesvartRiver : PriObjectRiver<BesvartMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): BesvartMelding =
        BesvartMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPOERSEL_BESVART, Pri.NotisType.serializer(), json),
            transaksjonId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            spinnInntektsmeldingId = Pri.Key.SPINN_INNTEKTSMELDING_ID.lesOrNull(UuidSerializer, json),
        )

    override fun BesvartMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        Metrics.forespoerslerBesvartFraSpleis.inc()

        val resultat =
            mapOf(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.KONTEKST_ID to transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId?.toJson(),
                        Key.SPINN_INNTEKTSMELDING_ID_V2 to spinnInntektsmeldingId?.toJson(),
                    ).mapValuesNotNull { it }
                        .toJson(),
            )
        // midlertidlig logging for SPINN_INNTEKTSMELDING_ID_V2 key
        logger.info("Sender fra pri-topic")
        sikkerLogger.info("Sender fra pri-topic:\n${resultat.toJson().toPretty()}")
        return resultat
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
            Log.priNotis(notisType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
