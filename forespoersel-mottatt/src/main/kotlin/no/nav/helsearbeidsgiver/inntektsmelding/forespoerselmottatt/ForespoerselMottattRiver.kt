package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class Melding(
    val notisType: Pri.NotisType,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val skalHaPaaminnelse: Boolean,
)

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattRiver : PriObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): Melding =
        Melding(
            notisType = Pri.Key.NOTIS.krev(Pri.NotisType.FORESPØRSEL_MOTTATT, Pri.NotisType.serializer(), json),
            transaksjonId = UUID.randomUUID(),
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            orgnr = Pri.Key.ORGNR.les(Orgnr.serializer(), json),
            fnr = Pri.Key.FNR.les(Fnr.serializer(), json),
            skalHaPaaminnelse = Pri.Key.SKAL_HA_PAAMINNELSE.les(Boolean.serializer(), json),
        )

    override fun Melding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPØRSEL_MOTTATT}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${json.toPretty()}")

        return mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(EventName.serializer()),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.FNR to fnr.toJson(),
                    Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
                ).toJson(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke videreformidle mottatt forespørsel. Arbeidsgiver fikk ikke beskjed om inntektsmeldingsbehov.".also {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error(it, error)
        }

        return null
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselMottattRiver),
            Log.priNotis(notisType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}

private fun Map<Pri.Key, JsonElement>.toPretty(): String =
    toJson(MapSerializer(Pri.Key.serializer(), JsonElement.serializer()))
        .toPretty()
