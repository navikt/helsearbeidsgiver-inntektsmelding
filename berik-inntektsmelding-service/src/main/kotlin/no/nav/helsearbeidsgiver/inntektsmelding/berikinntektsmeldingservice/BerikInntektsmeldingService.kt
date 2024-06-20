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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class BerikInntektsmeldingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific,
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET

    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER,
        Key.ARBEIDSGIVER_ID,
        Key.SKJEMA_INNTEKTSMELDING
    )

    override val dataKeys = setOf(
        Key.FORESPOERSEL_SVAR,
        Key.VIRKSOMHET,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.ARBEIDSTAKER_INFORMASJON,
    )

    private val step1Key = Key.FORESPOERSEL_SVAR

    private val step2Key = Key.VIRKSOMHET

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val startDataPar = lesStartDataPar(melding)

        when {
            isFinished(melding) -> onFinished(transaksjonId, melding, startDataPar)

            isOnStep2(melding) -> onStep2(melding, transaksjonId, startDataPar)

            isOnStep1(melding) -> onStep1(melding, transaksjonId, startDataPar)

            isOnStep0(melding) -> onStep0(melding, transaksjonId, startDataPar)

            else -> logger.info("Noe gikk galt") // TODO: Hva gjør vi her?
        }
    }

    private fun onStep0(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startDataPar: Array<Pair<Key, JsonElement>>,
    ) {
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
        ) {
            rapid
                .publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    *startDataPar,
                ).also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.HENT_TRENGER_IM),
                    ) {
                        logger.info("BerikInntektsmeldingService: emitting behov HENT_TRENGER_IM")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                    }
                }
        }
    }

    private fun onStep1(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startDataPar: Array<Pair<Key, JsonElement>>,
    ) {
        val forespoerselSvar = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_SVAR to forespoerselSvar.toJson(Forespoersel.serializer()),
                *startDataPar,
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.VIRKSOMHET),
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov VIRKSOMHET")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
    }

    private fun onStep2(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startDataPar: Array<Pair<Key, JsonElement>>,
    ) {
        val forespoerselSvar = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val virksomhet = Key.VIRKSOMHET.les(String.serializer(), melding)

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_SVAR to forespoerselSvar.toJson(Forespoersel.serializer()),
                Key.VIRKSOMHET to virksomhet.toJson(String.serializer()),
                *startDataPar,
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.FULLT_NAVN),
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov FULLT_NAVN")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
    }

    private fun onFinished(
        transaksjonId: UUID,
        melding: Map<Key, JsonElement>,
        startDataPar: Array<Pair<Key, JsonElement>>,
    ) {
        val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val sykmeldt = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
        val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
        val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)

        val inntektsmelding =
            mapInntektsmelding(
                forespoersel = forespoersel,
                    skjema = skjema,
                    fulltnavnArbeidstaker = sykmeldt.navn,
                    virksomhetNavn = virksomhetNavn,
                    innsenderNavn = arbeidsgiver.navn,
                )

        if (inntektsmelding.bestemmendeFraværsdag.isBefore(inntektsmelding.inntektsdato)) {
            "Bestemmende fraværsdag er før inntektsdato. Dette er ikke mulig. Spleis vil trolig spør om ny inntektsmelding.".also {
                logger.error(it)
                    sikkerLogger.error(it)
                }
            }

        logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $transaksjonId")
        logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
        rapid
            .publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.UUID to transaksjonId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    *startDataPar,
                ).also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val clientId = RedisKey.of(fail.transaksjonId, eventName)
            .read()

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
                        failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(String.serializer()),
                    ).toJsonStr(ResultJson.serializer())

                    // redisStore.set(RedisKey.of(clientId), resultJson) // TODO: Sjekke hvordan vi gjør dette
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
                // TODO: Sjekke hvordan vi ønske å gjøre dette
//                datafeil.onEach { (key, defaultVerdi) ->
//                    redisStore.set(RedisKey.of(fail.transaksjonId, key), defaultVerdi.toString())
//                }

                val meldingMedDefault = datafeil.toMap().plus(melding)

                onData(meldingMedDefault)
            } // TODO: Hvordan bør vi håndtere feil her?
        }
    }

    private fun RedisKey.read(): UUID? = redisStore.get(this)?.fromJson(UuidSerializer)

    private fun tomPerson(fnr: String): PersonDato =
        PersonDato(
            navn = "",
            fødselsdato = null,
            ident = fnr,
        )

    private fun lesStartDataPar(melding: Map<Key, JsonElement>): Array<Pair<Key, JsonElement>> {
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(Fnr.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(Fnr.serializer(), melding)

        return listOf(
            Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(Fnr.serializer()),
            Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(Fnr.serializer()),
        ).toTypedArray()
    }

    private fun isOnStep2(melding: Map<Key, JsonElement>) =
        melding.containsKey(step1Key) && melding.containsKey(step2Key) && !melding.containsKey(Key.BEHOV) // TODO: Kan vi ta bord !behov ?

    private fun isOnStep1(melding: Map<Key, JsonElement>) = !isOnStep2(melding) && melding.containsKey(step1Key) && !melding.containsKey(Key.BEHOV)

    private fun isOnStep0(melding: Map<Key, JsonElement>) =
        !isOnStep1(melding) && !isOnStep2(melding) && !isFinished(melding) && !melding.containsKey(Key.BEHOV)
}
