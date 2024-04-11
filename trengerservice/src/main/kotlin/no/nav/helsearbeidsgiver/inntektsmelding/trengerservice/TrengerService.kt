package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

const val UNDEFINED_FELT: String = "{}"

class TrengerService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val sikkerLogger = sikkerLogger()

    override val event = EventName.TRENGER_REQUESTED
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

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        sikkerLogger.info("${simpleName()} Dispatcher HENT_TRENGER_IM for $transaksjonId")

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.UUID to transaksjonId.toJson()
        )
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        sikkerLogger.info("Dispatcher for $transaksjonId with trans state 'in progress'")

        if (steg1Keys.all(melding::containsKey) && steg2Keys.none(melding::containsKey)) {
            val forespoersel = Key.FORESPOERSEL_SVAR.les(TrengerInntekt.serializer(), melding)

            sikkerLogger.info("${simpleName()} Dispatcher VIRKSOMHET for $transaksjonId")
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson()
            )

            sikkerLogger.info("${simpleName()} dispatcher FULLT_NAVN for $transaksjonId")
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.IDENTITETSNUMMER to forespoersel.fnr.toJson(),
                Key.ARBEIDSGIVER_ID to Key.ARBEIDSGIVER_FNR.lesOrNull(String.serializer(), melding).orEmpty().toJson()
            )

            val inntektsdato = forespoersel.forslagInntektsdato()
                ?: bestemmendeFravaersdag(
                    arbeidsgiverperioder = emptyList(),
                    egenmeldingsperioder = forespoersel.egenmeldingsperioder.map { Periode(it.fom, it.tom) },
                    sykmeldingsperioder = forespoersel.sykmeldingsperioder.map { Periode(it.fom, it.tom) }
                )

            sikkerLogger.info("${simpleName()} Dispatcher INNTEKT for $transaksjonId")
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.ORGNRUNDERENHET to forespoersel.orgnr.toJson(),
                Key.FNR to forespoersel.fnr.toJson(),
                Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson()
            )
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val clientId = redisStore.get(RedisKey.of(transaksjonId, EventName.TRENGER_REQUESTED))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            }
        } else {
            val foresporselSvar = Key.FORESPOERSEL_SVAR.les(TrengerInntekt.serializer(), melding)
            val sykmeldt = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
            val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
            val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
            val inntekt = melding[Key.INNTEKT].toString().takeIf { it != "\"$UNDEFINED_FELT\"" }?.fromJson(Inntekt.serializer())

            val feilReport = redisStore.get(RedisKey.of(transaksjonId, Feilmelding("")))?.fromJson(FeilReport.serializer())

            val trengerDataJson = TrengerData(
                forespoersel = foresporselSvar,
                fnr = foresporselSvar.fnr,
                orgnr = foresporselSvar.orgnr,
                personDato = sykmeldt,
                arbeidsgiver = arbeidsgiver,
                virksomhetNavn = virksomhetNavn,
                inntekt = inntekt,
                skjaeringstidspunkt = foresporselSvar.eksternBestemmendeFravaersdag(),
                fravarsPerioder = foresporselSvar.sykmeldingsperioder,
                egenmeldingsPerioder = foresporselSvar.egenmeldingsperioder,
                forespurtData = foresporselSvar.forespurtData,
                bruttoinntekt = inntekt?.gjennomsnitt(),
                tidligereinntekter = inntekt?.maanedOversikt,
                feilReport = feilReport
            )
                .toJsonStr(TrengerData.serializer())

            redisStore.set(RedisKey.of(clientId), trengerDataJson)
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.HENT_TRENGER_IM) {
            val feilReport = FeilReport(
                mutableListOf(
                    Feilmelding("Teknisk feil, prøv igjen senere.", -1, datafelt = Key.FORESPOERSEL_SVAR)
                )
            )

            sikkerLogger.info("terminate transaction id ${fail.transaksjonId} with evenname ${fail.event}")

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, fail.event))?.let(UUID::fromString)
            if (clientId != null) {
                redisStore.set(RedisKey.of(clientId), TrengerData(feilReport = feilReport).toJsonStr(TrengerData.serializer()))
            }
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
            val feilKey = RedisKey.of(fail.transaksjonId, Feilmelding(""))
            val feilReport = redisStore.get(feilKey)?.fromJson(FeilReport.serializer()) ?: FeilReport()
            feilReport.feil.addAll(
                datafeil.map { Feilmelding(it.feilmelding, datafelt = it.key) }
            )
            redisStore.set(feilKey, feilReport.toJsonStr(FeilReport.serializer()))
        }

        datafeil.onEach {
            redisStore.set(RedisKey.of(fail.transaksjonId, it.key), it.defaultVerdi.toString())
        }

        val meldingMedDefault = datafeil.associate { it.key to it.defaultVerdi }
            .plus(melding)

        if (dataKeys.all(meldingMedDefault::containsKey)) {
            finalize(meldingMedDefault)
        }
    }
}

private data class Datafeil(
    val key: Key,
    val feilmelding: String,
    val defaultVerdi: JsonElement
)
