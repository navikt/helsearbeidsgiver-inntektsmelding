package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publishNotNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.aktivtArbeidsforholdIPeriode
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.OffsetDateTime
import java.util.UUID

class LagreSelvbestemtImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.SELVBESTEMT_IM_MOTTATT
    override val startKeys = setOf(
        Key.SKJEMA_INNTEKTSMELDING,
        Key.ARBEIDSGIVER_FNR
    )
    override val dataKeys = setOf(
        Key.VIRKSOMHET,
        Key.PERSONER,
        Key.SELVBESTEMT_INNTEKTSMELDING,
        Key.ER_DUPLIKAT_IM,
        Key.SAK_ID,
        Key.ARBEIDSFORHOLD
    )

    private val steg1Keys = setOf(
        Key.VIRKSOMHET,
        Key.PERSONER,
        Key.ARBEIDSFORHOLD
    )
    private val steg2Keys = setOf(
        Key.SELVBESTEMT_INNTEKTSMELDING,
        Key.ER_DUPLIKAT_IM
    )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmeldingSelvbestemt.serializer(), melding)
        val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publishNotNull(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.SELVBESTEMT_ID to skjema.selvbestemtId?.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.ORGNRUNDERENHET to skjema.avsender.orgnr.toJson(Orgnr.serializer())
            )

            rapid.publishNotNull(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.SELVBESTEMT_ID to skjema.selvbestemtId?.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.FNR_LISTE to listOf(
                    skjema.sykmeldtFnr,
                    avsenderFnr
                ).toJson(Fnr.serializer())
            )

            rapid.publishNotNull(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                Key.IDENTITETSNUMMER to skjema.sykmeldtFnr.verdi.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.SELVBESTEMT_ID to skjema.selvbestemtId?.toJson()
            )
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            if (steg2Keys.all(melding::containsKey)) {
                val inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), melding)

                when (inntektsmelding.aarsakInnsending) {
                    AarsakInnsending.Endring -> {
                        finalize(melding)
                    }

                    AarsakInnsending.Ny -> {
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.OPPRETT_SELVBESTEMT_SAK.toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                        )
                            .also {
                                logger.info("Publiserte melding med behov '${BehovType.OPPRETT_SELVBESTEMT_SAK}'.")
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                            }
                    }
                }
            } else if (steg1Keys.all(melding::containsKey)) {
                val skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmeldingSelvbestemt.serializer(), melding)
                val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)
                val orgNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
                val personer = Key.PERSONER.les(personMapSerializer, melding)

                val sykmeldt = skjema.sykmeldtFnr.let {
                    personer[it.verdi] ?: tomPerson(it.verdi)
                }
                val avsender = avsenderFnr.let {
                    personer[it.verdi] ?: tomPerson(it.verdi)
                }

                val inntektsmelding = tilInntektsmelding(
                    skjema = skjema,
                    orgNavn = orgNavn,
                    sykmeldt = sykmeldt,
                    avsender = avsender
                )

                val arbeidsforholdListe = Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), melding)
                    .filter { it.arbeidsgiver.organisasjonsnummer == skjema.avsender.orgnr.verdi }
                val sykeperioder = skjema.sykmeldingsperioder
                val erAktivtArbeidsforhold = sykeperioder.aktivtArbeidsforholdIPeriode(arbeidsforholdListe)
                if (erAktivtArbeidsforhold) {

                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.LAGRE_SELVBESTEMT_IM.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                    )
                        .also {
                            logger.info("Publiserte melding med behov '${BehovType.LAGRE_SELVBESTEMT_IM}'.")
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                        }
                } else {
                    val clientId = redisStore.get(RedisKey.of(transaksjonId, event))?.let(UUID::fromString)
                    if (clientId == null) {
                        sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
                    } else {
                        "Mangler arbeidsforhold i perioden".also { feilmelding ->
                            logger.warn(feilmelding)
                            sikkerLogger.warn(feilmelding)
                            val resultJson = ResultJson(failure = feilmelding.toJson()).toJsonStr()
                            redisStore.set(RedisKey.of(clientId), resultJson)
                        }
                    }
                }
            } else {
                Unit
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.selvbestemtId(inntektsmelding.type.id)
        ) {
            val clientId = redisStore.get(RedisKey.of(transaksjonId, event))?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            } else {
                val resultJson = ResultJson(success = inntektsmelding.type.id.toJson()).toJsonStr()
                redisStore.set(RedisKey.of(clientId), resultJson)
            }

            if (!erDuplikat) {
                rapid.publish(
                    Key.EVENT_NAME to EventName.SELVBESTEMT_IM_LAGRET.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.SELVBESTEMT_ID to inntektsmelding.type.id.toJson(),
                    Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.event(EventName.SELVBESTEMT_IM_LAGRET)
                        ) {
                            logger.info("Publiserte melding.")
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                        }
                    }
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            val datafeil =
                when (utloesendeBehov) {
                    BehovType.VIRKSOMHET -> Key.VIRKSOMHET to "Ukjent virksomhet".toJson()

                    // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                    BehovType.FULLT_NAVN -> Key.PERSONER to emptyMap<String, JsonElement>().toJson()

                    else -> null
                }

            if (datafeil != null) {
                redisStore.set(RedisKey.of(fail.transaksjonId, datafeil.first), datafeil.second.toString())

                val meldingMedDefault = mapOf(datafeil) + melding

                return inProgress(meldingMedDefault)
            }

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                val selvbestemtId = Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, fail.utloesendeMelding.toMap())
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. selvbestemtId=$selvbestemtId")
            } else {
                val resultJson = ResultJson(failure = fail.feilmelding.toJson()).toJsonStr()
                redisStore.set(RedisKey.of(clientId), resultJson)
            }
        }
    }
}

fun tilInntektsmelding(
    skjema: SkjemaInntektsmeldingSelvbestemt,
    orgNavn: String,
    sykmeldt: Person,
    avsender: Person
): Inntektsmelding {
    val aarsakInnsending =
        if (skjema.selvbestemtId == null) {
            AarsakInnsending.Ny
        } else {
            AarsakInnsending.Endring
        }

    return Inntektsmelding(
        id = UUID.randomUUID(),
        type = Inntektsmelding.Type.Selvbestemt(
            id = skjema.selvbestemtId ?: UUID.randomUUID()
        ),
        sykmeldt = Sykmeldt(
            fnr = sykmeldt.fnr.let(::Fnr),
            navn = sykmeldt.navn
        ),
        avsender = Avsender(
            orgnr = skjema.avsender.orgnr,
            orgNavn = orgNavn,
            navn = avsender.navn,
            tlf = skjema.avsender.tlf
        ),
        sykmeldingsperioder = skjema.sykmeldingsperioder,
        agp = skjema.agp,
        inntekt = skjema.inntekt,
        refusjon = skjema.refusjon,
        aarsakInnsending = aarsakInnsending,
        mottatt = OffsetDateTime.now()
    )
}

private fun tomPerson(fnr: String): Person =
    Person(
        fnr = fnr,
        navn = "",
        foedselsdato = Person.foedselsdato(fnr)
    )
