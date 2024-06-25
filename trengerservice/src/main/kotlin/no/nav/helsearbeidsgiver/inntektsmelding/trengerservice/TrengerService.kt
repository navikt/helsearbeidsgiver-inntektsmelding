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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

private const val UNDEFINED_FELT = "{}"
private const val UKJENT_NAVN = "Ukjent navn"

class TrengerService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TRENGER_REQUESTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ARBEIDSGIVER_FNR
    )
    override val dataKeys = setOf(
        Key.FORESPOERSEL_SVAR,
        Key.VIRKSOMHET,
        Key.PERSONER,
        Key.INNTEKT
    )

    private val steg1Keys = setOf(
        Key.FORESPOERSEL_SVAR
    )
    private val steg2Keys = setOf(
        Key.VIRKSOMHET,
        Key.PERSONER,
        Key.INNTEKT
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)

        if (isFinished(melding)) {
            finish(melding)
        } else if (steg1Keys.all(melding::containsKey) && steg2Keys.none(melding::containsKey)) {
            val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
            val inntektsdato = forespoersel.forslagInntektsdato()

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
            )
                .also { loggBehovPublisert(BehovType.VIRKSOMHET, it) }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.FNR_LISTE to listOf(
                    forespoersel.fnr.let(::Fnr),
                    avsenderFnr
                ).toJson(Fnr.serializer())
            )
                .also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                Key.FNR to forespoersel.fnr.toJson(),
                Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson()
            )
                .also { loggBehovPublisert(BehovType.INNTEKT, it) }
        } else if (startKeys.all(melding::containsKey) && steg1Keys.none(melding::containsKey)) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson()
            )
                .also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
        }
    }

    private fun finish(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)
        val foresporsel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
        val personer = Key.PERSONER.les(personMapSerializer, melding)

        val sykmeldtNavn = personer[foresporsel.fnr.let(::Fnr)]?.navn ?: UKJENT_NAVN
        val avsenderNavn = personer[avsenderFnr]?.navn ?: UKJENT_NAVN

        val inntekt = melding[Key.INNTEKT].toString().takeIf { it != "\"$UNDEFINED_FELT\"" }?.fromJson(Inntekt.serializer())

        val feil = redisStore.get(RedisKey.feilmelding(transaksjonId))?.fromJson(feilMapSerializer)

        val resultJson =
            ResultJson(
                success = HentForespoerselResultat(
                    sykmeldtNavn = sykmeldtNavn,
                    avsenderNavn = avsenderNavn,
                    orgNavn = virksomhetNavn,
                    inntekt = inntekt,
                    forespoersel = foresporsel,
                    feil = feil.orEmpty()
                )
                    .toJson(HentForespoerselResultat.serializer())
            )
                .toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(transaksjonId), resultJson)
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val overkommeligFeil = when (utloesendeBehov) {
            BehovType.VIRKSOMHET ->
                Datafeil(
                    Key.VIRKSOMHET,
                    "Vi klarte ikke å hente navn på virksomhet.",
                    "Ukjent virksomhet".toJson()
                )

            BehovType.HENT_PERSONER ->
                Datafeil(
                    Key.PERSONER,
                    "Vi klarte ikke å hente navn på personer.",
                    // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                    emptyMap<Fnr, Person>().toJson(personMapSerializer)
                )

            BehovType.INNTEKT ->
                Datafeil(
                    Key.INNTEKT,
                    "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                    UNDEFINED_FELT.toJson()
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

            val meldingMedDefault = mapOf(overkommeligFeil.key to overkommeligFeil.defaultVerdi)
                .plus(melding)

            onData(meldingMedDefault)
        } else {
            "Uoverkommelig feil oppsto under henting av data til forhåndsutfylling av skjema.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }

            val resultJson = ResultJson(
                failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson()
            )
                .toJson(ResultJson.serializer())

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)
        }
    }

    private fun loggBehovPublisert(behovType: BehovType, publisert: JsonElement) {
        MdcUtils.withLogFields(
            Log.behov(behovType)
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
    val defaultVerdi: JsonElement
)

private val feilMapSerializer =
    MapSerializer(
        Key.serializer(),
        String.serializer()
    )
