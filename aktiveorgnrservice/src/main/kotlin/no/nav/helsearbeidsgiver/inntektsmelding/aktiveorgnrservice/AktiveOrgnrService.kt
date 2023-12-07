package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class AktiveOrgnrService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {
    private val sikkerLogger = sikkerLogger()
    private val logger = logger()
    override val event: EventName = EventName.AKTIVE_ORGNR_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore = redisStore,
                event = event,
                dataFelter = arrayOf(Key.FNR, Key.ARBEIDSGIVER_FNR),
                mainListener = it,
                rapidsConnection = rapid
            )
        }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    Key.ARBEIDSFORHOLD,
                    Key.ORG_RETTIGHETER,
                    Key.ARBEIDSTAKER_INFORMASJON,
                    Key.VIRKSOMHETER
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()
        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        when (transaction) {
            Transaction.NEW -> {
                val innloggetFnr = json[Key.ARBEIDSGIVER_FNR]?.fromJson(String.serializer())
                val sykemeldtFnr = json[Key.FNR]?.fromJson(String.serializer())
                if (innloggetFnr != null && sykemeldtFnr != null) {
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
                        Key.IDENTITETSNUMMER to innloggetFnr.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                        Key.IDENTITETSNUMMER to sykemeldtFnr.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                        Key.IDENTITETSNUMMER to sykemeldtFnr.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                } else {
                    MdcUtils.withLogFields(
                        Log.klasse(this),
                        Log.event(event),
                        Log.transaksjonId(transaksjonId)
                    ) {
                        "Mangler arbeidsgiverFnr eller arbeidstakerFnr."
                            .also {
                                sikkerLogger.error(it)
                                logger.error(it)
                            }
                        terminate(message.createFail("Ukjent Feil oppstod"))
                    }
                }
            }

            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(transaksjonId))) {
                    val arbeidsforholdListe = RedisKey.of(transaksjonId, Key.ARBEIDSFORHOLD).read()?.fromJson(Arbeidsforhold.serializer().list())
                    val orgrettigheter = RedisKey.of(transaksjonId, Key.ORG_RETTIGHETER).read()?.fromJson(String.serializer().set())
                    val result = trekkUtArbeidsforhold(arbeidsforholdListe, orgrettigheter)
                    result.onSuccess { arbeidsgivere ->
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.ORGNRUNDERENHETER to arbeidsgivere.toJson(String.serializer())
                        )
                    }
                    result.onFailure {
                        val feilmelding = it.message ?: "Ukjent feil oppstod"
                        MdcUtils.withLogFields(
                            Log.klasse(this),
                            Log.event(event),
                            Log.transaksjonId(transaksjonId)
                        ) {
                            sikkerLogger.error(feilmelding)
                            logger.error(feilmelding)
                        }
                        terminate(message.createFail(feilmelding))
                    }
                }
            }

            else -> {
                logger.info("Transaksjon $transaction er ikke støttet.")
            }
        }

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")
        }
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()
        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val clientId = RedisKey.of(transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        val virksomheter = RedisKey.of(transaksjonId, Key.VIRKSOMHETER)
            .read()
            ?.fromJson(
                MapSerializer(String.serializer(), String.serializer())
            )

        val fulltNavn = RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
            .read()
            ?.fromJson(PersonDato.serializer()) ?: PersonDato("Ukjent Navn", null, "")

        if (clientId != null && virksomheter != null) {
            val gyldigeUnderenheter =
                virksomheter.map {
                    GyldigUnderenhet(
                        orgnrUnderenhet = it.key,
                        virksomhetsnavn = it.value
                    )
                }.toList()
            val gyldigResponse = AktiveOrgnrResponse(
                fulltNavn = fulltNavn.navn,
                underenheter = gyldigeUnderenheter
            ).toJson(AktiveOrgnrResponse.serializer())
            RedisKey.of(clientId).write(gyldigResponse)
        } else {
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.transaksjonId(transaksjonId)
            ) {
                if (clientId == null) {
                    "Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!".also { feilmelding ->
                        sikkerLogger.error(feilmelding)
                        logger.error(feilmelding)
                    }
                }
                if (virksomheter == null) {
                    "Kunne ikke finne virksomheter for transaksjonId $transaksjonId i Redis!".also { feilmelding ->
                        sikkerLogger.error(feilmelding)
                        logger.error(feilmelding)
                    }
                }
            }
            terminate(message.createFail("Ukjent feil oppstod"))
        }
    }

    override fun terminate(fail: Fail) {
        val transaksjonId = Key.UUID.les(UuidSerializer, fail.toJsonMessage().toJsonMap())

        val clientId = RedisKey.of(transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        if (clientId != null) {
            val feilResponse = AktiveOrgnrResponse(
                underenheter = emptyList(),
                feilReport = FeilReport(
                    feil = mutableListOf(
                        Feilmelding(melding = fail.feilmelding)
                    )
                )
            ).toJson(AktiveOrgnrResponse.serializer())
            RedisKey.of(clientId).write(feilResponse)
        }
    }

    private fun trekkUtArbeidsforhold(arbeidsforholdListe: List<Arbeidsforhold>?, orgrettigheter: Set<String>?): Result<List<String>> {
        return if (arbeidsforholdListe.isNullOrEmpty()) {
            Result.failure(Exception("Fant ingen aktive arbeidsforhold"))
        } else if (orgrettigheter.isNullOrEmpty()) {
            Result.failure(Exception("Må ha orgrettigheter for å kunne hente virksomheter"))
        } else {
            val arbeidsgivere =
                arbeidsforholdListe
                    .filterOrgnr(
                        *orgrettigheter.toTypedArray()
                    )
                    .orgnrMedAktivtArbeidsforhold()
            if (arbeidsgivere.isEmpty()) {
                Result.failure(Exception("Fant ingen aktive arbeidsforhold"))
            } else {
                Result.success(arbeidsgivere)
            }
        }
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, Key.ARBEIDSFORHOLD),
        RedisKey.of(uuid, Key.ORG_RETTIGHETER)
    )

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }
}

@Serializable
data class AktiveOrgnrResponse(
    val fulltNavn: String? = null,
    val underenheter: List<GyldigUnderenhet>,
    val feilReport: FeilReport? = null
)

@Serializable
data class GyldigUnderenhet(
    val orgnrUnderenhet: String,
    val virksomhetsnavn: String
)
