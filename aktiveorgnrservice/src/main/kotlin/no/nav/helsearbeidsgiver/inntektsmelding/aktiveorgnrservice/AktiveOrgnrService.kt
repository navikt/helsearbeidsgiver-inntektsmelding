package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdListe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
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
            StatefullEventListener(redisStore, event, arrayOf(DataFelt.FNR, DataFelt.ARBEIDSGIVER_FNR), it, rapid)
        }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.ARBEIDSFORHOLD,
                    DataFelt.ORGNRUNDERENHET
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
                    Key.IDENTITETSNUMMER to json[DataFelt.ARBEIDSGIVER_FNR].toString().toJson(),
                    Key.UUID to transaksjonId.toJson()
                )*/
                // TODO: Skriv om denne
                json[DataFelt.ARBEIDSGIVER_FNR]!!.fromJson(String.serializer())?.toJson()?.also {
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
                if (isDataCollected(*step1data(transaksjonId))) {
                    val arbeidsforholdListe = RedisKey.of(transaksjonId, DataFelt.ARBEIDSFORHOLD).read()
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
                            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                            Key.DATA to "".toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            DataFelt.ORGNRUNDERENHET to arbeidsgivere.first().toJson()
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

        val GYLDIG_AKTIVE_ORGNR_RESPONSE = """
            {
                "underenheter": [{"orgnrUnderenhet": "test-orgnr", "virksomhetsnavn": "test-orgnavn"}]
            }
        """.toJson()

        RedisKey.of(clientId!!).write(GYLDIG_AKTIVE_ORGNR_RESPONSE)
    }

    override fun terminate(fail: Fail) {
        TODO("Not yet implemented")
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.ARBEIDSFORHOLD)
    )

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }
}
