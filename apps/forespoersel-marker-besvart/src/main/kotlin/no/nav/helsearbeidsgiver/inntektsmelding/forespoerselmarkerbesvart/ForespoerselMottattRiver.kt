package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class MottattMelding(
    val notisType: Pri.NotisType,
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val forespoerselFraBro: ForespoerselFraBro,
    val skalHaPaaminnelse: Boolean,
)

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattRiver : PriObjectRiver<MottattMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): MottattMelding =
        MottattMelding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPØRSEL_MOTTATT, Pri.NotisType.serializer(), json),
            kontekstId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            forespoerselFraBro = Pri.Key.FORESPOERSEL.les(ForespoerselFraBro.serializer(), json),
            skalHaPaaminnelse = Pri.Key.SKAL_HA_PAAMINNELSE.les(Boolean.serializer(), json),
        )

    override fun MottattMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun MottattMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPØRSEL_MOTTATT}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.FORESPOERSEL to forespoerselFraBro.toForespoersel().toJson(Forespoersel.serializer()),
                    Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
                ).toJson(),
        )
    }

    override fun MottattMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke videreformidle mottatt forespørsel. Arbeidsgiver fikk ikke beskjed om inntektsmeldingsbehov.".also {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error(it, error)
        }

        return null
    }

    override fun MottattMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselMottattRiver),
            Log.priNotis(notisType),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}

private fun Map<Pri.Key, JsonElement>.toPretty(): String =
    toJson(MapSerializer(Pri.Key.serializer(), JsonElement.serializer()))
        .toPretty()
