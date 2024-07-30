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
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class BerikInntektsmeldingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
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
        Key.INNTEKTSMELDING_DOKUMENT,
        Key.ER_DUPLIKAT_IM
    )

    private val step1Key = Key.FORESPOERSEL_SVAR

    private val step2Key = Key.VIRKSOMHET

    private val step3Keys = setOf(Key.ARBEIDSGIVER_INFORMASJON, Key.ARBEIDSTAKER_INFORMASJON)

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val startdata = lesStartdata(melding)

        when {
            isFinished(melding) -> onFinished(transaksjonId, melding, startdata)

            isOnStep3(melding) -> onStep3(melding, transaksjonId, startdata)

            isOnStep2(melding) -> onStep2(melding, transaksjonId, startdata)

            isOnStep1(melding) -> onStep1(melding, transaksjonId, startdata)

            isOnStep0(melding) -> onStep0(transaksjonId, startdata)

            else -> logger.info("Noe gikk galt") // TODO: Hva gjør vi her?
        }
    }

    private fun onStep0(
        transaksjonId: UUID,
        startdata: Array<Pair<Key, JsonElement>>
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid
                .publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    *startdata
                ).also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.HENT_TRENGER_IM)
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
        startdata: Array<Pair<Key, JsonElement>>
    ) {
        val forespoerselSvar = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_SVAR to forespoerselSvar.toJson(Forespoersel.serializer()),
                *startdata
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.VIRKSOMHET)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov VIRKSOMHET")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
    }

    private fun onStep2(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startdata: Array<Pair<Key, JsonElement>>
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
                *startdata
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.FULLT_NAVN)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov FULLT_NAVN")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
    }

    private fun onStep3(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startdata: Array<Pair<Key, JsonElement>>
    ) {
        val forespoerselSvar = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val virksomhet = Key.VIRKSOMHET.les(String.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(String.serializer(), melding)
        val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val arbeidstaker = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
        val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
        val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)

        val inntektsmelding =
            mapInntektsmelding(
                forespoersel = forespoersel,
                skjema = skjema,
                fulltnavnArbeidstaker = arbeidstaker.navn,
                virksomhetNavn = virksomhetNavn,
                innsenderNavn = arbeidsgiver.navn
            )

        if (inntektsmelding.bestemmendeFraværsdag.isBefore(inntektsmelding.inntektsdato)) {
            "Bestemmende fraværsdag er før inntektsdato. Dette er ikke mulig. Spleis vil trolig spør om ny inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                Key.FORESPOERSEL_SVAR to forespoerselSvar.toJson(Forespoersel.serializer()),
                Key.VIRKSOMHET to virksomhet.toJson(String.serializer()),
                Key.ARBEIDSTAKER_INFORMASJON to arbeidstaker.toJson(PersonDato.serializer()),
                Key.ARBEIDSGIVER_INFORMASJON to arbeidsgiver.toJson(PersonDato.serializer()),
                *startdata
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.PERSISTER_IM)
                ) {
                    logger.info("BerikInntektsmeldingService: emitting behov PERSISTER_IM")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
            }
    }

    private fun onFinished(
        transaksjonId: UUID,
        melding: Map<Key, JsonElement>,
        startdata: Array<Pair<Key, JsonElement>>
    ) {
        val inntektsmelding = Key.INNTEKTSMELDING_DOKUMENT.les(Inntektsmelding.serializer(), melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)

        logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $transaksjonId")
        logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")

        if (!erDuplikat) {
            rapid
                .publish(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    *startdata
                ).also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            // TODO: Hva gjør vi her dersom vi ikke klarte å hente forespørselen? Retry-mekanisme?
            return
        }

        val datafeil =
            when (utloesendeBehov) {
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
            val bumerangdata = fail.utloesendeMelding.toMap()
                .minus(listOf(Key.BEHOV, Key.EVENT_NAME))
                .toList()
                .toTypedArray()
            val meldingMedDefault = datafeil.toMap().plus(melding).plus(bumerangdata)
            onData(meldingMedDefault)
        }
    }

    private fun tomPerson(fnr: String): PersonDato =
        PersonDato(
            navn = "",
            fødselsdato = null,
            ident = fnr
        )

    private fun lesStartdata(melding: Map<Key, JsonElement>): Array<Pair<Key, JsonElement>> {
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(Fnr.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(Fnr.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)

        return listOf(
            Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(Fnr.serializer()),
            Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(Fnr.serializer()),
            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(Innsending.serializer())
        ).toTypedArray()
    }

    private fun isOnStep3(melding: Map<Key, JsonElement>): Boolean =
        melding.containsKey(step1Key) &&
            melding.containsKey(step2Key) &&
            melding.containsKeys(step3Keys) &&
            !isFinished(melding)

    private fun isOnStep2(melding: Map<Key, JsonElement>): Boolean =
        melding.containsKey(step1Key) &&
            melding.containsKey(step2Key) &&
            !isOnStep3(melding) &&
            !isFinished(melding)

    private fun isOnStep1(melding: Map<Key, JsonElement>): Boolean =
        melding.containsKey(step1Key) &&
            !isOnStep2(melding) &&
            !isOnStep3(melding) &&
            !isFinished(melding)

    private fun isOnStep0(melding: Map<Key, JsonElement>): Boolean =
        !isOnStep1(melding) &&
            !isOnStep2(melding) &&
            !isOnStep3(melding) &&
            !isFinished(melding)

    private fun <K, V> Map<K, V>.containsKeys(keys: Set<K>): Boolean = keys.all { this.containsKey(it) }
}
