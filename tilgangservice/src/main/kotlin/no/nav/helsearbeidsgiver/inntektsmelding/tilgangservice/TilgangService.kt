package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class TilgangService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.TILGANG_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.ORGNRUNDERENHET.str,
                    DataFelt.TILGANG.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.FNR.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (forespoerselId == null) {
            publishFail(message)
            return
        }
        val fields = loggFelterNotNull(transaksjonId, forespoerselId)

        MdcUtils.withLogFields(
            *fields
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")

            when (transaction) {
                Transaction.NEW -> {
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.HENT_IM_ORGNR.toJson(),
                        DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                        .also {
                            MdcUtils.withLogFields(
                                Log.behov(BehovType.HENT_IM_ORGNR)
                            ) {
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                            }
                        }
                }

                Transaction.IN_PROGRESS -> {
                    val orgnrKey = RedisKey.of(transaksjonId.toString(), DataFelt.ORGNRUNDERENHET)

                    if (isDataCollected(orgnrKey)) {
                        val orgnr = orgnrKey.read()

                        val fnr = RedisKey.of(transaksjonId.toString(), DataFelt.FNR)
                            .read()
                        if (orgnr == null || fnr == null) {
                            publishFail(message)
                            sikkerLogger.error("kunne ikke lese orgnr og / eller fnr fra Redis")
                            return
                        }
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                            DataFelt.ORGNRUNDERENHET to orgnr.toJson(),
                            DataFelt.FNR to fnr.toJson(),
                            Key.UUID to transaksjonId.toJson()
                        )
                            .also {
                                MdcUtils.withLogFields(
                                    Log.behov(BehovType.TILGANGSKONTROLL)
                                ) {
                                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                                }
                            }
                    } else {
                        sikkerLogger.error("Transaksjon er underveis, men mangler data. Dette bør aldri skje, ettersom vi kun venter på én datapakke.")
                    }
                }
                else -> {
                    sikkerLogger.error("Støtte på forutsett transaksjonstype: $transaction")
                }
            }
        }
    }

    private fun publishFail(message: JsonMessage) {
        rapid.publish(message.createFail("Kunne ikke lese data fra Redis!").toJsonMessage().toJson())
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()?.let(UUID::fromString)
        if (clientId == null) {
            sikkerLogger.error("Kunne ikke lese clientId for $transaksjonId fra Redis")
        }

        val tilgang = RedisKey.of(transaksjonId.toString(), DataFelt.TILGANG).read()
        val feil = RedisKey.of(transaksjonId.toString(), Feilmelding("")).read()

        val tilgangJson = TilgangData(
            tilgang = tilgang?.fromJson(Tilgang.serializer()),
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(TilgangData.serializer())

        RedisKey.of(clientId.toString()).write(tilgangJson)

        val logFields = loggFelterNotNull(transaksjonId, clientId)

        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.info("$event fullført.")
        }
    }

    override fun terminate(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()
            ?.let(UUID::fromString)

        val feil = RedisKey.of(transaksjonId.toString(), Feilmelding(""))
            .read()

        val feilResponse = TilgangData(
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(TilgangData.serializer())
        if (clientId == null) {
            sikkerLogger.error("$event forsøkt terminert, kunne ikke finne $transaksjonId i redis!")
        }
        RedisKey.of(clientId.toString()).write(feilResponse)

        val logFields = loggFelterNotNull(transaksjonId, clientId)
        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }

    override fun onError(feil: Fail): Transaction {
        val transaksjonId = feil.uuid ?: throw IllegalStateException("Feil mangler transaksjon-ID.")

        val manglendeDatafelt = when (feil.behov) {
            BehovType.HENT_IM_ORGNR -> DataFelt.ORGNRUNDERENHET
            BehovType.TILGANGSKONTROLL -> DataFelt.TILGANG
            else -> null
        }

        return if (manglendeDatafelt != null) {
            val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, manglendeDatafelt)

            sikkerLogger.error("Mottok feilmelding: '${feilmelding.melding}'")

            val feilKey = RedisKey.of(transaksjonId, feilmelding)

            val feilReport = feilKey.read()
                ?.fromJson(FeilReport.serializer())
                .orDefault(FeilReport())
                .also {
                    it.feil.add(feilmelding)
                }
                .toJson(FeilReport.serializer())

            feilKey.write(feilReport)

            Transaction.TERMINATE
        } else {
            Transaction.IN_PROGRESS
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    // Veldig stygt, ta med clientId i loggfelter når den eksisterer
    // TODO: skriv heller om MDCUtils.log. Fjern dette...
    private fun loggFelterNotNull(
        transaksjonId: UUID,
        clientId: UUID?
    ): Array<Pair<String, String>> {
        val logs = arrayOf(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        )
        val logFields = clientId?.let {
            logs +
                arrayOf(
                    Log.clientId(clientId)
                )
        } ?: logs
        return logFields
    }
}
