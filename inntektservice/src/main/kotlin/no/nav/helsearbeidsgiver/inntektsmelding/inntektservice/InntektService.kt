package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektData
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
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
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class InntektService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()
    private val logger = logger()

    override val event: EventName = EventName.INNTEKT_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.INNTEKT.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.SKJAERINGSTIDSPUNKT.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()
            ?.let(UUID::fromString)
        if (forespoerselId == null) {
            sikkerLogger.error("kunne ikke finne forespørselId for transaksjon $transaksjonId i Redis!")
            logger.error("kunne ikke finne forespørselId for transaksjon $transaksjonId i Redis!")
            return
        }
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")

            when (transaction) {
                is Transaction.New -> {
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                        DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
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

                is Transaction.InProgress -> {
                    val forspoerselKey = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_SVAR)

                    if (isDataCollected(forspoerselKey)) {
                        val forespoersel = forspoerselKey.read()?.fromJson(TrengerInntekt.serializer())
                        val skjaeringstidspunkt = RedisKey.of(transaksjonId.toString(), DataFelt.SKJAERINGSTIDSPUNKT).read()
                        if (forespoersel == null || skjaeringstidspunkt == null) {
                            logger.error("Klarte ikke å finne forespørsel eller skjæringstidspunkt i Redis!")
                            sikkerLogger.error("Klarte ikke å finne data i Redis - forespørsel: $forespoersel og skjæringstidspunkt $skjaeringstidspunkt")
                            return
                        }

                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.INNTEKT.toJson(),
                            DataFelt.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                            DataFelt.FNR to forespoersel.fnr.toJson(),
                            DataFelt.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson(),
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
                else -> {
                    sikkerLogger.error("Støtte på forutsett transaksjonstype: $transaction")
                }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId.toString(), event)
            .read()
            ?.let(UUID::fromString)

        if (clientId == null) {
            sikkerLogger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
            logger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
        }
        val inntekt = RedisKey.of(transaksjonId.toString(), DataFelt.INNTEKT).read()
        val feil = RedisKey.of(transaksjonId.toString(), Feilmelding("")).read()

        val inntektJson = InntektData(
            inntekt = inntekt?.fromJson(Inntekt.serializer()),
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(InntektData.serializer())

        RedisKey.of(clientId.toString()).write(inntektJson)
        val logFields = loggFelterNotNull(transaksjonId, clientId)

        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.info("$event fullført.")
        }
    }

    override fun terminate(fail: Fail) {
        val clientId = RedisKey.of(fail.uuid!!, event)
            .read()
            ?.let(UUID::fromString)
        if (clientId == null) {
            sikkerLogger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.uuid} i Redis")
            logger.error("Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.uuid} i Redis")
        }
        val feil = RedisKey.of(fail.uuid!!, Feilmelding("")).read()

        val feilResponse = InntektData(
            feil = feil?.fromJson(FeilReport.serializer())
        )
            .toJson(InntektData.serializer())

        RedisKey.of(clientId.toString()).write(feilResponse)
        val logFields = loggFelterNotNull(fail.uuid.let(UUID::fromString), clientId)
        MdcUtils.withLogFields(
            *logFields
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }

    override fun onError(feil: Fail): Transaction {
        val transaksjonId = feil.uuid ?: throw IllegalStateException("Feil mangler transaksjon-ID.")

        val (feilmelding, transaction) = when (feil.behov) {
            BehovType.HENT_TRENGER_IM -> {
                val feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, DataFelt.FORESPOERSEL_SVAR)

                feilmelding to Transaction.Terminate(feil)
            }
            BehovType.INNTEKT -> {
                val feilmelding = Feilmelding(
                    "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                    datafelt = DataFelt.INNTEKT
                )

                RedisKey.of(transaksjonId, DataFelt.INNTEKT).write(JsonObject(emptyMap()))

                feilmelding to null
            }
            else -> null to null
        }

        if (feilmelding != null) {
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
        }

        return transaction ?: Transaction.InProgress
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    // Veldig stygt, ta med clientId i loggfelter når den eksisterer
    // TODO: skriv heller om MDCUtils.log
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
