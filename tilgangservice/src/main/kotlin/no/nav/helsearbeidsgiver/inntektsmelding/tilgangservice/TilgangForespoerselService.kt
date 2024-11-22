package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class TilgangForespoerselService(
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
) : ServiceMed2Steg<
        TilgangForespoerselService.Steg0,
        TilgangForespoerselService.Steg1,
        TilgangForespoerselService.Steg2,
    >() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
        val avsenderFnr: Fnr,
    )

    data class Steg1(
        val forespoersel: Forespoersel,
    )

    data class Steg2(
        val tilgang: Tilgang,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            tilgang = Key.TILGANG.les(Tilgang.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        ).toJson(),
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.HENT_TRENGER_IM),
                ) {
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                }
            }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.ORGNRUNDERENHET to steg1.forespoersel.orgnr.toJson(),
                                Key.FNR to steg0.avsenderFnr.toJson(),
                            ),
                        ).toJson(),
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.TILGANGSKONTROLL),
                ) {
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                }
            }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val tilgang =
            ResultJson(
                success = steg2.tilgang.toJson(Tilgang.serializer()),
            )

        redisStore.skrivResultat(steg0.transaksjonId, tilgang)

        sikkerLogger.info("$eventName fullført.")
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(fail.transaksjonId),
        ) {
            val tilgangResultat =
                ResultJson(
                    failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
                )

            redisStore.skrivResultat(fail.transaksjonId, tilgangResultat)

            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@TilgangForespoerselService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
