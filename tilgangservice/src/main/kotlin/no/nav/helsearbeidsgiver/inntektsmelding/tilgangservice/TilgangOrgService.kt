package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangResultat
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TilgangOrgService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TILGANG_ORG_REQUESTED
    override val startKeys = setOf(
        Key.ORGNRUNDERENHET,
        Key.FNR
    )
    override val dataKeys = setOf(
        Key.TILGANG
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val fnr = Key.FNR.les(String.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        ) {
            if (isFinished(melding)) {
                val tilgang = Key.TILGANG.les(Tilgang.serializer(), melding)

                val tilgangJson = TilgangResultat(
                    tilgang = tilgang
                )
                    .toJson(TilgangResultat.serializer())

                redisStore.set(RedisKey.of(transaksjonId), tilgangJson)

                sikkerLogger.info("$eventName fullført.")
            } else {
                rapid.publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.FNR to fnr.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.TILGANGSKONTROLL)
                        ) {
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                        }
                    }
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val tilgangResultat = TilgangResultat(
                feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
            )

            sikkerLogger.error("Returnerer feilmelding: '${tilgangResultat.feilmelding}'")

            redisStore.set(RedisKey.of(fail.transaksjonId), tilgangResultat.toJson(TilgangResultat.serializer()))

            sikkerLogger.error("$eventName terminert.")
        }
    }
}
