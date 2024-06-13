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
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

const val UNDEFINED_FELT: String = "{}"

class TrengerService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TRENGER_REQUESTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ARBEIDSGIVER_ID
    )
    override val dataKeys = setOf(
        Key.FORESPOERSEL_SVAR,
        Key.VIRKSOMHET,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.INNTEKT
    )

    private val steg1Keys = setOf(
        Key.FORESPOERSEL_SVAR
    )
    private val steg2Keys = setOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.INNTEKT
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        if (isFinished(melding)) {
            finish(melding)
        } else if (steg1Keys.all(melding::containsKey) && steg2Keys.none(melding::containsKey)) {
            val forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
            val inntektsdato = forespoersel.forslagInntektsdato()

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
            )
                .also { loggBehovPublisert(BehovType.VIRKSOMHET, it) }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.IDENTITETSNUMMER to forespoersel.fnr.toJson(),
                Key.ARBEIDSGIVER_ID to Key.ARBEIDSGIVER_FNR.lesOrNull(String.serializer(), melding).orEmpty().toJson()
            )
                .also { loggBehovPublisert(BehovType.FULLT_NAVN, it) }

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                Key.FNR to forespoersel.fnr.toJson(),
                Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson()
            )
                .also { loggBehovPublisert(BehovType.INNTEKT, it) }
        } else if (startKeys.all(melding::containsKey) && steg1Keys.none(melding::containsKey)) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson()
            )
                .also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
        }
    }

    private fun finish(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val foresporselSvar = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding)
        val sykmeldt = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
        val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
        val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
        val inntekt = melding[Key.INNTEKT].toString().takeIf { it != "\"$UNDEFINED_FELT\"" }?.fromJson(Inntekt.serializer())

        val feil = redisStore.get(RedisKey.feilmelding(transaksjonId))?.fromJson(feilMapSerializer)

        val resultJson =
            ResultJson(
                success = HentForespoerselResultat(
                    sykmeldtNavn = sykmeldt.navn,
                    avsenderNavn = arbeidsgiver.navn,
                    orgNavn = virksomhetNavn,
                    inntekt = inntekt,
                    forespoersel = foresporselSvar,
                    feil = feil.orEmpty()
                )
                    .toJson(HentForespoerselResultat.serializer())
            )
                .toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(transaksjonId), resultJson)
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            sikkerLogger.info("terminate transaction id ${fail.transaksjonId} with eventname ${fail.event}")

            val resultJson = ResultJson(
                failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(String.serializer())
            )
                .toJson(ResultJson.serializer())

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)

            return
        }

        val datafeil = when (utloesendeBehov) {
            BehovType.VIRKSOMHET ->
                listOf(
                    Datafeil(
                        Key.VIRKSOMHET,
                        "Vi klarte ikke å hente virksomhet navn.",
                        "Ukjent navn".toJson()
                    )
                )

            BehovType.FULLT_NAVN ->
                listOf(
                    Datafeil(
                        Key.ARBEIDSTAKER_INFORMASJON,
                        "Vi klarte ikke å hente arbeidstaker informasjon.",
                        PersonDato("Ukjent navn", null, "").toJson(PersonDato.serializer())
                    ),
                    Datafeil(
                        Key.ARBEIDSGIVER_INFORMASJON,
                        "Vi klarte ikke å hente arbeidsgiver informasjon.",
                        PersonDato("Ukjent navn", null, "").toJson(PersonDato.serializer())
                    )
                )

            BehovType.INNTEKT ->
                listOf(
                    Datafeil(
                        Key.INNTEKT,
                        "Vi har problemer med å hente inntektsopplysninger. Du kan legge inn beregnet månedsinntekt manuelt, eller prøv igjen senere.",
                        UNDEFINED_FELT.toJson()
                    )
                )

            else ->
                emptyList()
        }

        if (datafeil.isNotEmpty()) {
            val feilKey = RedisKey.feilmelding(fail.transaksjonId)
            val gamleFeil = redisStore.get(feilKey)?.fromJson(feilMapSerializer)

            val alleFeil = gamleFeil.orEmpty() + datafeil.associate { it.key to it.feilmelding }

            redisStore.set(feilKey, alleFeil.toJson(feilMapSerializer))
        }

        datafeil.onEach {
            redisStore.set(RedisKey.of(fail.transaksjonId, it.key), it.defaultVerdi)
        }

        val meldingMedDefault = datafeil.associate { it.key to it.defaultVerdi }
            .plus(melding)

        onData(meldingMedDefault)
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
