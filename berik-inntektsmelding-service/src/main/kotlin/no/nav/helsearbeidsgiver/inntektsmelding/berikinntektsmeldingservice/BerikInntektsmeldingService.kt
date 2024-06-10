package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class BerikInntektsmeldingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKTSMELDING_SKJEMA_MOTTATT
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.SKJEMA_INNTEKTSMELDING,
        Key.ORGNRUNDERENHET
    )
    override val dataKeys = setOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.INNTEKTSMELDING_DOKUMENT,
        Key.FORESPOERSEL_SVAR
    )

    override fun onStart(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson()
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.HENT_TRENGER_IM)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov HENT_TRENGER_IM")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson()
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.VIRKSOMHET)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov VIRKSOMHET")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
                Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson()
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.FULLT_NAVN)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov FULLT_NAVN")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
        }
    }

    override fun onData(melding: Map<Key, JsonElement>) {
        if (isFinished(melding)) {
            val transaksjonId = Key.UUID.les(UuidSerializer, melding)
            val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

            val clientId = RedisKey.of(transaksjonId, eventName)
                .read()
                ?.fromJson(UuidSerializer)

            if (clientId == null) {
                "Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!".also {
                    sikkerLogger.error(it)
                    logger.error(it)
                }
            } else {
                val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
                val sykmeldt = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
                val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
                val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
                val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)
                val inntektsmelding = mapInntektsmelding(
                    forespoersel = forespoersel,
                    skjema = skjema,
                    fulltnavnArbeidstaker = sykmeldt.navn,
                    virksomhetNavn = virksomhetNavn,
                    innsenderNavn = arbeidsgiver.navn
                )

                if (inntektsmelding.bestemmendeFraværsdag.isBefore(inntektsmelding.inntektsdato)) {
                    "Bestemmende fraværsdag er før inntektsdato. Dette er ikke mulig. Spleis vil trolig spør om ny inntektsmelding.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }

                logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $transaksjonId") // TODO: Endre loggingen her.
                logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
                rapid.publish(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer())
                )
                    .also {
                        logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                        sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                    }
            }
        } else {
            "Service skal aldri være \"underveis\".".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val clientId = RedisKey.of(fail.transaksjonId, eventName)
            .read()
            ?.fromJson(UuidSerializer)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId)
            ) {
                "Forsøkte å terminere, men fant ikke clientID for transaksjon ${fail.transaksjonId} i Redis".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
            }
        } else {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

            if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
                sikkerLogger.info("terminate transaction id ${fail.transaksjonId} with eventname ${fail.event}")

                // TODO: Gir det mening å drive å skrive til Redis når vi er i async-verden? Hvordan behandler vi feil nå som ikke lenger Routen står og venter på svar?

                val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, fail.event))?.fromJson(UuidSerializer)
                if (clientId != null) {
                    val resultJson = ResultJson(
                        failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(String.serializer())
                    )
                        .toJsonStr(ResultJson.serializer())

                    redisStore.set(RedisKey.of(clientId), resultJson)
                }
                return
            }

            val datafeil = when (utloesendeBehov) {
                BehovType.VIRKSOMHET -> {
                    listOf(
                        Key.VIRKSOMHET to "Ukjent virksomhet".toJson()
                    )
                }

                BehovType.FULLT_NAVN -> {
                    val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                    val avsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

                    listOf(
                        Key.ARBEIDSTAKER_INFORMASJON to tomPerson(sykmeldtFnr).toJson(PersonDato.serializer()),
                        Key.ARBEIDSGIVER_INFORMASJON to tomPerson(avsenderFnr).toJson(PersonDato.serializer())
                    )
                }

                else -> {
                    emptyList()
                }
            }

            if (datafeil.isNotEmpty()) {
                datafeil.onEach { (key, defaultVerdi) ->
                    redisStore.set(RedisKey.of(fail.transaksjonId, key), defaultVerdi.toString())
                }

                val meldingMedDefault = datafeil.toMap().plus(melding)

                onData(meldingMedDefault)
            } // TODO: Hvordan bør vi håndtere feil her?
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun tomPerson(fnr: String): PersonDato =
        PersonDato(
            navn = "",
            fødselsdato = null,
            ident = fnr
        )
}
