package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektData
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class InntektService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.INNTEKT_REQUESTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID,
        Key.SKJAERINGSTIDSPUNKT
    )
    override val dataKeys = listOf(
        Key.FORESPOERSEL_SVAR,
        Key.INNTEKT
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson()
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

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            val forspoerselKey = RedisKey.of(transaksjonId, Key.FORESPOERSEL_SVAR)

            if (isDataCollected(setOf(forspoerselKey))) {
                val forespoersel = forspoerselKey.read()?.fromJson(TrengerInntekt.serializer())
                val skjaeringstidspunkt = RedisKey.of(transaksjonId, Key.SKJAERINGSTIDSPUNKT).read()
                if (forespoersel == null || skjaeringstidspunkt == null) {
                    logger.error("Klarte ikke å finne forespørsel eller skjæringstidspunkt i Redis!")
                    sikkerLogger.error("Klarte ikke å finne data i Redis - forespørsel: $forespoersel og skjæringstidspunkt $skjaeringstidspunkt")
                    return
                }

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.INNTEKT.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.FNR to forespoersel.fnr.toJson(),
                    Key.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson(),
                    Key.UUID to transaksjonId.toJson()
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.INNTEKT)
                        ) {
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                        }
                    }
            } else {
                sikkerLogger.error("Transaksjon er underveis, men mangler data. Dette bør aldri skje, ettersom vi kun venter på én datapakke.")
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        val clientId = RedisKey.of(transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
            logger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
        } else {
            val inntekt = RedisKey.of(transaksjonId, Key.INNTEKT).read()

            val feil = RedisKey.of(transaksjonId, Feilmelding("")).read()

            val inntektJson = InntektData(
                inntekt = inntekt?.fromJson(Inntekt.serializer()),
                feil = feil?.fromJson(FeilReport.serializer())
            )
                .toJson(InntektData.serializer())

            RedisKey.of(clientId).write(inntektJson)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.info("$event fullført.")
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.INNTEKT) {
            val feilmelding = Feilmelding(
                "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                datafelt = Key.INNTEKT
            )

            "Legger til feilmelding: '${feilmelding.melding}'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            RedisKey.of(fail.transaksjonId, feilmelding).write(
                FeilReport(
                    mutableListOf(feilmelding)
                ).toJson(FeilReport.serializer())
            )

            RedisKey.of(fail.transaksjonId, Key.INNTEKT).write(JsonObject(emptyMap()))

            // TODO bruk finalize (sjekk andre servicer for tilsvarende feil)
            return inProgress(melding)
        }

        val feilReport = if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, Key.FORESPOERSEL_SVAR)

            "Returnerer feilmelding: '${feilmelding.melding}'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            FeilReport(
                mutableListOf(feilmelding)
            )
        } else {
            FeilReport()
        }

        val clientId = RedisKey.of(fail.transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.transaksjonId} i Redis")
                logger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.transaksjonId} i Redis")
            }
        } else {
            val feilResponse = InntektData(
                feil = feilReport
            )
                .toJson(InntektData.serializer())

            RedisKey.of(clientId).write(feilResponse)

            MdcUtils.withLogFields(
                Log.clientId(clientId),
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("$event terminert.")
            }
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)
}
