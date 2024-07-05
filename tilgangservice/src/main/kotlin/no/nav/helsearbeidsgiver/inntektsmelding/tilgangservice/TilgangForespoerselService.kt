package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

class TilgangForespoerselService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.FNR
    )
    override val dataKeys = setOf(
        Key.FORESPOERSEL_SVAR,
        Key.TILGANG
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val avsenderFnr = Key.FNR.les(Fnr.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            if (isFinished(melding)) {
                val tilgang = Key.TILGANG.les(Tilgang.serializer(), melding)

                val tilgangJson = TilgangResultat(
                    tilgang = tilgang
                )
                    .toJson(TilgangResultat.serializer())

                redisStore.set(RedisKey.of(transaksjonId), tilgangJson)

                sikkerLogger.info("$eventName fullført.")
            } else if (Key.FORESPOERSEL_SVAR in melding) {
                val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)

                rapid.publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.FNR to avsenderFnr.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.TILGANGSKONTROLL)
                        ) {
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                        }
                    }
            } else {
                rapid.publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.HENT_TRENGER_IM)
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
