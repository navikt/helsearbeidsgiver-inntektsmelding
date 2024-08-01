package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

private const val UNDEFINED_FELT = "{}"
private const val UKJENT_NAVN = "Ukjent navn"
private const val UKJENT_VIRKSOMHET = "Ukjent virksomhet"

data class Steg0(
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val avsenderFnr: Fnr,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

sealed class Steg2 {
    data class Komplett(
        val orgnrMedNavn: Map<String, String>,
        val personer: Map<Fnr, Person>,
        val inntekt: Inntekt?,
    ) : Steg2()

    data object Delvis : Steg2()
}

class HentForespoerselService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TRENGER_REQUESTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
            Key.ARBEIDSGIVER_FNR,
        )
    override val dataKeys =
        setOf(
            Key.FORESPOERSEL_SVAR,
            Key.VIRKSOMHETER,
            Key.PERSONER,
            Key.INNTEKT,
        )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 {
        val orgnrMedNavn = runCatching { Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }
        val inntekt =
            runCatching {
                melding[Key.INNTEKT].toString().takeIf { it != "\"$UNDEFINED_FELT\"" }?.fromJson(Inntekt.serializer())
            }

        val results = listOf(orgnrMedNavn, personer, inntekt)

        return if (results.all { it.isSuccess }) {
            Steg2.Komplett(
                orgnrMedNavn = orgnrMedNavn.getOrThrow(),
                personer = personer.getOrThrow(),
                inntekt = inntekt.getOrThrow(),
            )
        } else if (results.any { it.isSuccess }) {
            Steg2.Delvis
        } else {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
    }

    override fun utfoerSteg0(steg0: Steg0) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val inntektsdato = steg1.forespoersel.forslagInntektsdato()

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.ORGNR_UNDERENHETER to setOf(steg1.forespoersel.orgnr).toJson(String.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.FNR_LISTE to
                            setOf(
                                steg1.forespoersel.fnr.let(::Fnr),
                                steg0.avsenderFnr,
                            ).toJson(Fnr.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.ORGNRUNDERENHET to steg1.forespoersel.orgnr.toJson(),
                        Key.FNR to steg1.forespoersel.fnr.toJson(),
                        Key.INNTEKTSDATO to inntektsdato.toJson(),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_INNTEKT, it) }
    }

    override fun utfoerSteg2(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        if (steg2 is Steg2.Komplett) {
            val sykmeldtNavn = steg2.personer[steg1.forespoersel.fnr.let(::Fnr)]?.navn ?: UKJENT_NAVN
            val avsenderNavn = steg2.personer[steg0.avsenderFnr]?.navn ?: UKJENT_NAVN
            val orgNavn = steg2.orgnrMedNavn[steg1.forespoersel.orgnr] ?: UKJENT_VIRKSOMHET

            val feil = redisStore.get(RedisKey.feilmelding(steg0.transaksjonId))?.fromJson(feilMapSerializer)

            val resultJson =
                ResultJson(
                    success =
                        HentForespoerselResultat(
                            sykmeldtNavn = sykmeldtNavn,
                            avsenderNavn = avsenderNavn,
                            orgNavn = orgNavn,
                            inntekt = steg2.inntekt,
                            forespoersel = steg1.forespoersel,
                            feil = feil.orEmpty(),
                        ).toJson(HentForespoerselResultat.serializer()),
                ).toJson(ResultJson.serializer())

            redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val overkommeligFeil =
            when (utloesendeBehov) {
                BehovType.HENT_VIRKSOMHET_NAVN ->
                    Datafeil(
                        Key.VIRKSOMHETER,
                        "Vi klarte ikke å hente navn på virksomhet.",
                        // Lesing av virksomhetsnavn bruker allerede defaults, så trenger bare map-struktur her
                        emptyMap<String, String>().toJson(),
                    )

                BehovType.HENT_PERSONER ->
                    Datafeil(
                        Key.PERSONER,
                        "Vi klarte ikke å hente navn på personer.",
                        // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                        emptyMap<Fnr, Person>().toJson(personMapSerializer),
                    )

                BehovType.HENT_INNTEKT ->
                    Datafeil(
                        Key.INNTEKT,
                        "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                        UNDEFINED_FELT.toJson(),
                    )

                else ->
                    null
            }

        if (overkommeligFeil != null) {
            val feilmeldingKey = RedisKey.feilmelding(fail.transaksjonId)
            val defaultVerdiKey = RedisKey.of(fail.transaksjonId, overkommeligFeil.key)

            val gamleFeil = redisStore.get(feilmeldingKey)?.fromJson(feilMapSerializer)

            val alleFeil = gamleFeil.orEmpty() + mapOf(overkommeligFeil.key to overkommeligFeil.feilmelding)

            redisStore.set(feilmeldingKey, alleFeil.toJson(feilMapSerializer))
            redisStore.set(defaultVerdiKey, overkommeligFeil.defaultVerdi)

            val meldingMedDefault =
                mapOf(overkommeligFeil.key to overkommeligFeil.defaultVerdi)
                    .plus(melding)

            onData(meldingMedDefault)
        } else {
            "Uoverkommelig feil oppsto under henting av data til forhåndsutfylling av skjema.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }

            val resultJson =
                ResultJson(
                    failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
                ).toJson(ResultJson.serializer())

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentForespoerselService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    private fun loggBehovPublisert(
        behovType: BehovType,
        publisert: JsonElement,
    ) {
        MdcUtils.withLogFields(
            Log.behov(behovType),
        ) {
            "Publiserte melding med behov $behovType.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }
}

private data class Datafeil(
    val key: Key,
    val feilmelding: String,
    val defaultVerdi: JsonElement,
)

private val feilMapSerializer =
    MapSerializer(
        Key.serializer(),
        String.serializer(),
    )
