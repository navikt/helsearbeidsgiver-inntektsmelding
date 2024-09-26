package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.PriObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class VedtaksperiodeIdForespoerselSvarMelding(
    val eventName: EventName,
    val behovType: Pri.BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselSvar: ForespoerselListeSvar,
)

class VedtaksperiodeIdForespoerselSvarRiver : PriObjectRiver<VedtaksperiodeIdForespoerselSvarMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Pri.Key, JsonElement>): VedtaksperiodeIdForespoerselSvarMelding {
        val forespoerselSvar = Pri.Key.LØSNING.les(ForespoerselListeSvar.serializer(), json)
        val boomerang = forespoerselSvar.boomerang.toMap()

        return VedtaksperiodeIdForespoerselSvarMelding(
            eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerang),
            behovType = Pri.Key.BEHOV.krev(Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE, Pri.BehovType.serializer(), json),
            transaksjonId = Key.UUID.les(UuidSerializer, boomerang),
            data = boomerang[Key.DATA]?.toMap().orEmpty(),
            forespoerselSvar = forespoerselSvar,
        )
    }

    override fun VedtaksperiodeIdForespoerselSvarMelding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok løsning på pri-topic om $behovType.")
        sikkerLogger.info("Mottok løsning på pri-topic:\n$json")

        val forespoersler = forespoerselSvar.resultat.associate { it.forespoerselId to it.toForespoersel() }
        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.FORESPOERSLER_SVAR to
                            forespoersler.toJson(
                                serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
                            ),
                    ).toJson(),
        )
    }

    override fun VedtaksperiodeIdForespoerselSvarMelding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente forespørsler for vedtaksperiode-IDer. Ukjent feil.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.DATA to data.toJson(),
                    ).toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun VedtaksperiodeIdForespoerselSvarMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@VedtaksperiodeIdForespoerselSvarRiver),
            Log.event(eventName),
            Log.behov(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE),
            Log.transaksjonId(transaksjonId),
        )

    private fun ForespoerselListeSvar.Forespoersel.toForespoersel(): Forespoersel =
        Forespoersel(
            type = type,
            orgnr = orgnr.toString(),
            fnr = fnr,
            vedtaksperiodeId = vedtaksperiodeId,
            sykmeldingsperioder = sykmeldingsperioder,
            egenmeldingsperioder = egenmeldingsperioder,
            bestemmendeFravaersdager = bestemmendeFravaersdager.mapKeys { it.key.toString() },
            forespurtData = forespurtData,
            erBesvart = erBesvart,
        )
}
