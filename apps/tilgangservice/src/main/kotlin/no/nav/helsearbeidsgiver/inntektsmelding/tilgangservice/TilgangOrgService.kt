package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class TilgangOrgService(
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
) : ServiceMed1Steg<TilgangOrgService.Steg0, TilgangOrgService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TILGANG_ORG_REQUESTED

    data class Steg0(
        val kontekstId: UUID,
        val orgnr: Orgnr,
        val fnr: Fnr,
    )

    data class Steg1(
        val tilgang: Tilgang,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), melding),
            fnr = Key.FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            tilgang = Key.TILGANG.les(Tilgang.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                key = steg0.fnr,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.ORGNR_UNDERENHET to steg0.orgnr.toJson(),
                                Key.FNR to steg0.fnr.toJson(),
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

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val resultat =
            ResultJson(
                success = steg1.tilgang.toJson(Tilgang.serializer()),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultat)

        sikkerLogger.info("$eventName fullført.")
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.kontekstId(fail.kontekstId),
        ) {
            val resultat =
                ResultJson(
                    failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
                )

            redisStore.skrivResultat(fail.kontekstId, resultat)

            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@TilgangOrgService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
        )
}
