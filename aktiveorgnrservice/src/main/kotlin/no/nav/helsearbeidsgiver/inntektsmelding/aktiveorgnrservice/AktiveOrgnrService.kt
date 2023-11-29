package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdListe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
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
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
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
            StatefullEventListener(redisStore, event, arrayOf(Key.FNR, Key.ARBEIDSGIVER_FNR), it, rapid)
        }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    Key.ARBEIDSFORHOLD,
                    // Key.ORG_RETTIGHETER,
                    Key.ORGNRUNDERENHETER,
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
                /* TODO: Hent rettigheter fra altinn
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
                    Key.IDENTITETSNUMMER to json[Key.ARBEIDSGIVER_FNR].toString().toJson(),
                    Key.UUID to transaksjonId.toJson()
                ) */
                // TODO: Skriv om denne
                json[Key.ARBEIDSGIVER_FNR]!!.fromJson(String.serializer())?.toJson()?.also {
                    // Hent arbeidsforhold fra aareg
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                        Key.IDENTITETSNUMMER to it,
                        Key.UUID to transaksjonId.toJson()
                    )
                }
            }
            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step2data(transaksjonId))) {
                    val virksomheter = RedisKey.of(transaksjonId, Key.VIRKSOMHETER).read()?.let {
                        Json.decodeFromString<Map<String, String>>(it)
                    }
                    if (virksomheter != null) {
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.DATA to "".toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.ORGNRUNDERENHETER to virksomheter.keys.toList().toJson(String.serializer()),
                            Key.VIRKSOMHETER to Json.encodeToJsonElement(virksomheter)
                        )
                    }
                }
                if (isDataCollected(*step1data(transaksjonId))) {
                    val arbeidsforholdListe = RedisKey.of(transaksjonId, Key.ARBEIDSFORHOLD).read()
                    if (arbeidsforholdListe != null) {
                        // TODO: hent arbeidsgivere fra altinn respons
                        val arbeidsgivere =
                            arbeidsforholdListe
                                .fromJson(ArbeidsforholdListe.serializer())
                                .arbeidsforhold
                                .medOrgnr(
                                    "810007702",
                                    "810007842",
                                    "810008032",
                                    "810007982"
                                )
                                .orgnrMedAktivtArbeidsforhold(
                                    LocalDate.of(2018, 1, 5)
                                )

                        // TODO: hent virksomhetsnavn fra brreg
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.DATA to "".toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.ORGNRUNDERENHET to arbeidsgivere.first().toJson()
                        )
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.ORGNRUNDERENHET to arbeidsgivere.first().toJson(),
                            Key.ORGNRUNDERENHETER to arbeidsgivere.toJson(String.serializer())
                        )
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

        if (clientId == null) {
            sikkerLogger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
            logger.error("Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!")
            terminate(message.createFail("Fant ikke clientId for transaksjonId $transaksjonId i Redis!"))
        }
        val virksomheter = RedisKey.of(transaksjonId, Key.VIRKSOMHETER).read()?.let {
            Json.decodeFromString<Map<String, String>>(it)
        }
        if (virksomheter != null) {
            val m: List<Map<String, String>> = virksomheter.map {
                mapOf(
                    "orgnrUnderenhet" to it.key,
                    "virksomhetsnavn" to it.value
                )
            }.toList()
            val s = Json.encodeToJsonElement(mapOf("underenheter" to m))
            RedisKey.of(clientId!!).write(s)
        }
    }

    override fun terminate(fail: Fail) {
        TODO("Not yet implemented")
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, Key.ARBEIDSFORHOLD)
        // RedisKey.of(uuid, Key.ORG_RETTIGHETER)
    )

    private fun step2data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, Key.VIRKSOMHETER)
    )

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }
}
