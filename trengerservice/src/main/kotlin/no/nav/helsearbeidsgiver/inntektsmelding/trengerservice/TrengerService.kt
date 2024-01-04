package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import kotlinx.serialization.KSerializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerData
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

const val UNDEFINED_FELT: String = "{}"

class TrengerService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val sikkerLogger = sikkerLogger()

    override val event = EventName.TRENGER_REQUESTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID,
        Key.ARBEIDSGIVER_ID
    )
    override val dataKeys = listOf(
        Key.FORESPOERSEL_SVAR,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.VIRKSOMHET,
        Key.INNTEKT
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)

        sikkerLogger.info("Dispatcher HENT_TRENGER_IM for $uuid")
        sikkerLogger.info("${simpleName()} Dispatcher HENT_TRENGER_IM for $uuid")

        val agFnr = message[Key.ARBEIDSGIVER_ID.str].asText()

        redisStore.set(RedisKey.of(uuid, Key.ARBEIDSGIVER_FNR), agFnr) // ta vare på denne til vi slår opp fullt navn

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.UUID to uuid.toJson()
        )
    }

    override fun inProgress(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)

        sikkerLogger.info("Dispatcher for $uuid with trans state 'in progress'")

        message.interestedIn(Key.FORESPOERSEL_SVAR.str)

        if (isDataCollected(step1data(uuid)) && !message[Key.FORESPOERSEL_SVAR.str].isMissingNode) {
            val forespoersel = redisStore.get(RedisKey.of(uuid, Key.FORESPOERSEL_SVAR))!!.fromJson(TrengerInntekt.serializer())
            sikkerLogger.info("${simpleName()} Dispatcher VIRKSOMHET for $uuid")
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to uuid.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
            )
            sikkerLogger.info("${simpleName()} dispatcher FULLT_NAVN for $uuid")
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to uuid.toJson(),
                Key.IDENTITETSNUMMER to forespoersel.fnr.toJson(),
                Key.ARBEIDSGIVER_ID to redisStore.get(RedisKey.of(uuid, Key.ARBEIDSGIVER_FNR)).orEmpty().toJson()
            )

            val skjaeringstidspunkt = forespoersel.skjaeringstidspunkt
                ?: finnSkjaeringstidspunkt(forespoersel.egenmeldingsperioder + forespoersel.sykmeldingsperioder)

            if (skjaeringstidspunkt != null) {
                sikkerLogger.info("${simpleName()} Dispatcher INNTEKT for $uuid")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.INNTEKT.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                    Key.FNR to forespoersel.fnr.toJson(),
                    Key.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson()
                )
            } else {
                "Fant ikke skjaeringstidspunkt å hente inntekt for.".also {
                    sikkerLogger.error("$it forespoersel=$forespoersel")
                    val fail = Fail(
                        feilmelding = it,
                        event = event,
                        transaksjonId = uuid,
                        forespoerselId = forespoerselId,
                        utloesendeMelding = message.toJson().parseJson()
                    )
                    onError(message, fail)
                }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val melding = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val clientId = redisStore.get(RedisKey.of(transaksjonId, EventName.TRENGER_REQUESTED))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            }
        } else {
            val foresporselSvar = redisStore.get(RedisKey.of(transaksjonId, Key.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())
            val inntekt = redisStore.get(RedisKey.of(transaksjonId, Key.INNTEKT))?.fromJson(Inntekt.serializer())
            val feilReport: FeilReport? = redisStore.get(RedisKey.of(transaksjonId, Feilmelding("")))?.fromJson(FeilReport.serializer())

            val trengerData = TrengerData(
                fnr = foresporselSvar?.fnr,
                orgnr = foresporselSvar?.orgnr,
                personDato = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer()),
                arbeidsgiver = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSGIVER_INFORMASJON))?.fromJson(PersonDato.serializer()),
                virksomhetNavn = redisStore.get(RedisKey.of(transaksjonId, Key.VIRKSOMHET)),
                inntekt = redisStore.get(RedisKey.of(transaksjonId, Key.INNTEKT))?.fromJsonWithUndefined(Inntekt.serializer()),
                skjaeringstidspunkt = foresporselSvar?.skjaeringstidspunkt,
                fravarsPerioder = foresporselSvar?.sykmeldingsperioder,
                egenmeldingsPerioder = foresporselSvar?.egenmeldingsperioder,
                forespurtData = foresporselSvar?.forespurtData,
                bruttoinntekt = inntekt?.gjennomsnitt(),
                tidligereinntekter = inntekt?.maanedOversikt,
                feilReport = feilReport
            )

            val json = trengerData.toJsonStr(TrengerData.serializer())
            redisStore.set(RedisKey.of(clientId), json)
        }
    }

    override fun onError(message: JsonMessage, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        var feilmelding: Feilmelding? = null
        if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            val feilReport = FeilReport(
                mutableListOf(
                    Feilmelding("Teknisk feil, prøv igjen senere.", -1, datafelt = Key.FORESPOERSEL_SVAR)
                )
            )

            sikkerLogger.info("terminate transaction id ${fail.transaksjonId} with evenname ${fail.event}")

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, fail.event))?.let(UUID::fromString)
            if (clientId != null) {
                redisStore.set(RedisKey.of(clientId), TrengerData(feilReport = feilReport).toJsonStr(TrengerData.serializer()))
            }
            return
        } else if (utloesendeBehov == BehovType.VIRKSOMHET) {
            feilmelding = Feilmelding("Vi klarte ikke å hente virksomhet navn.", datafelt = Key.VIRKSOMHET)
            redisStore.set(RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET), "Ukjent navn")
        } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
            feilmelding = Feilmelding("Vi klarte ikke å hente arbeidstaker informasjon.", datafelt = Key.ARBEIDSTAKER_INFORMASJON)
            redisStore.set(
                RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON),
                PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer())
            )
            redisStore.set(
                RedisKey.of(fail.transaksjonId, Key.ARBEIDSGIVER_INFORMASJON),
                PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer())
            )
        } else if (utloesendeBehov == BehovType.INNTEKT) {
            feilmelding = Feilmelding(
                "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                datafelt = Key.INNTEKT
            )
            redisStore.set(RedisKey.of(fail.transaksjonId, Key.INNTEKT), UNDEFINED_FELT)
        }
        if (feilmelding != null) {
            val feilKey = RedisKey.of(fail.transaksjonId, feilmelding)
            val feilReport: FeilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.add(feilmelding)
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
        }
        return inProgress(message)
    }

    private fun step1data(uuid: UUID): List<RedisKey> =
        listOf(
            RedisKey.of(uuid, Key.FORESPOERSEL_SVAR)
        )

    private fun <T> String.fromJsonWithUndefined(serializer: KSerializer<T>): T? {
        if (this == UNDEFINED_FELT) return null
        return this.fromJson(serializer)
    }
}
