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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
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
class TrengerService(private val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {

    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.TRENGER_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal {
            StatefullDataKanal(
                listOf(
                    Key.FORESPOERSEL_SVAR,
                    Key.ARBEIDSTAKER_INFORMASJON,
                    Key.ARBEIDSGIVER_INFORMASJON,
                    Key.VIRKSOMHET,
                    Key.INNTEKT
                ).toTypedArray(),
                event,
                it,
                rapidsConnection,
                redisStore
            )
        }
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                listOf(Key.FORESPOERSEL_ID, Key.ARBEIDSGIVER_ID).toTypedArray(),
                it,
                rapidsConnection
            )
        }
    }

    override fun onError(feil: Fail): Transaction {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), feil.utloesendeMelding.toMap())

        var feilmelding: Feilmelding? = null
        if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            feilmelding = Feilmelding("Teknisk feil, prøv igjen senere.", -1, datafelt = Key.FORESPOERSEL_SVAR)
            val feilKey = RedisKey.of(feil.transaksjonId!!, feilmelding)
            val feilReport: FeilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.add(feilmelding)
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
            return Transaction.TERMINATE
        } else if (utloesendeBehov == BehovType.VIRKSOMHET) {
            feilmelding = Feilmelding("Vi klarte ikke å hente virksomhet navn.", datafelt = Key.VIRKSOMHET)
            redisStore.set(RedisKey.of(feil.transaksjonId!!, Key.VIRKSOMHET), "Ukjent navn")
        } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
            feilmelding = Feilmelding("Vi klarte ikke å hente arbeidstaker informasjon.", datafelt = Key.ARBEIDSTAKER_INFORMASJON)
            redisStore.set(
                RedisKey.of(feil.transaksjonId!!, Key.ARBEIDSTAKER_INFORMASJON),
                PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer())
            )
            redisStore.set(
                RedisKey.of(feil.transaksjonId!!, Key.ARBEIDSGIVER_INFORMASJON),
                PersonDato("Ukjent navn", null, "").toJsonStr(PersonDato.serializer())
            )
        } else if (utloesendeBehov == BehovType.INNTEKT) {
            feilmelding = Feilmelding(
                "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                datafelt = Key.INNTEKT
            )
            redisStore.set(RedisKey.of(feil.transaksjonId!!, Key.INNTEKT), UNDEFINED_FELT)
        }
        if (feilmelding != null) {
            val feilKey = RedisKey.of(feil.transaksjonId!!, feilmelding)
            val feilReport: FeilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.add(feilmelding)
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
        }
        return Transaction.IN_PROGRESS
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid = message[Key.UUID.str].asText().let(UUID::fromString)
        sikkerLogger.info("Dispatcher for $uuid with trans state $transaction")
        if (transaction == Transaction.NEW) {
            sikkerLogger.info("Dispatcher HENT_TRENGER_IM for $uuid")
            sikkerLogger.info("${simpleName()} Dispatcher HENT_TRENGER_IM for $uuid")
            val agFnr = message[Key.ARBEIDSGIVER_ID.str].asText()
            redisStore.set(RedisKey.of(uuid, Key.ARBEIDSGIVER_FNR), agFnr) // ta vare på denne til vi slår opp fullt navn
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, Key.FORESPOERSEL_ID))!!.toJson(),
                Key.UUID to uuid.toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            message.interestedIn(Key.FORESPOERSEL_SVAR.str)
            if (isDataCollected(*step1data(uuid)) && !message[Key.FORESPOERSEL_SVAR.str].isMissingNode) {
                val forespoersel = redisStore.get(RedisKey.of(uuid, Key.FORESPOERSEL_SVAR))!!.fromJson(TrengerInntekt.serializer())

                sikkerLogger.info("${simpleName()} Dispatcher VIRKSOMHET for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
                )
                sikkerLogger.info("${simpleName()} dispatcher FULLT_NAVN for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespoersel.fnr.toJson(),
                    Key.ARBEIDSGIVER_ID to redisStore.get(RedisKey.of(uuid, Key.ARBEIDSGIVER_FNR)).orEmpty().toJson()
                )
                /*
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                    Key.UUID to uuid.toJson(),
                    Key.IDENTITETSNUMMER to forespurtData.fnr.toJson()
                )
                */

                val skjaeringstidspunkt = forespoersel.skjaeringstidspunkt
                    ?: finnSkjaeringstidspunkt(forespoersel.egenmeldingsperioder + forespoersel.sykmeldingsperioder)

                if (skjaeringstidspunkt != null) {
                    sikkerLogger.info("${simpleName()} Dispatcher INNTEKT for $uuid")
                    rapidsConnection.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.INNTEKT.toJson(),
                        Key.UUID to uuid.toJson(),
                        Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                        Key.FNR to forespoersel.fnr.toJson(),
                        Key.SKJAERINGSTIDSPUNKT to skjaeringstidspunkt.toJson()
                    )
                } else {
                    val forespoerselId = redisStore.get(RedisKey.of(uuid, Key.FORESPOERSEL_ID))

                    "Fant ikke skjaeringstidspunkt å hente inntekt for.".also {
                        sikkerLogger.error("$it forespoersel=$forespoersel")
                        val fail = Fail(
                            feilmelding = it,
                            event = event,
                            transaksjonId = uuid,
                            forespoerselId = forespoerselId.let(UUID::fromString),
                            utloesendeMelding = message.toJson().parseJson()
                        )
                        onError(fail)
                    }
                }
            }
        } else {
            sikkerLogger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid= $uuid")
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

    override fun terminate(fail: Fail) {
        sikkerLogger.info("terminate transaction id ${fail.transaksjonId} with evenname ${fail.event}")
        val clientId = redisStore.get(RedisKey.of(fail.transaksjonId!!, fail.event))?.let(UUID::fromString)
        // @TODO kan vare smartere her. Kan definere feilmeldingen i Feil message istedenfor å hardkode det i TrengerService. Vi også ikke trenger å sende alle andre ikke kritiske feilmeldinger hvis vi har noe kritisk
        val feilReport: FeilReport = redisStore.get(RedisKey.of(fail.transaksjonId!!, Feilmelding("")))!!.fromJson(FeilReport.serializer())
        if (clientId != null) {
            redisStore.set(RedisKey.of(clientId), TrengerData(feilReport = feilReport).toJsonStr(TrengerData.serializer()))
        }
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, Key.FORESPOERSEL_SVAR)
    )

    fun <T> String.fromJsonWithUndefined(serializer: KSerializer<T>): T? {
        if (this == UNDEFINED_FELT) return null
        return this.fromJson(serializer)
    }
}
