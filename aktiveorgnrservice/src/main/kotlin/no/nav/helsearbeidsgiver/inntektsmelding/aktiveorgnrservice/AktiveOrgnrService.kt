package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class AktiveOrgnrService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val sikkerLogger = sikkerLogger()
    private val logger = logger()

    override val event = EventName.AKTIVE_ORGNR_REQUESTED
    override val startKeys = setOf(
        Key.FNR,
        Key.ARBEIDSGIVER_FNR
    )
    override val dataKeys = setOf(
        Key.ORG_RETTIGHETER,
        Key.ARBEIDSFORHOLD,
        Key.PERSONER,
        Key.VIRKSOMHETER
    )

    private val step1Keys = setOf(
        Key.ORG_RETTIGHETER,
        Key.ARBEIDSFORHOLD,
        Key.PERSONER
    )
    private val step2Keys = setOf(
        Key.VIRKSOMHETER
    )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        val innloggetFnr = melding[Key.ARBEIDSGIVER_FNR]?.fromJson(Fnr.serializer())
        val sykmeldtFnr = melding[Key.FNR]?.fromJson(Fnr.serializer())
        if (innloggetFnr != null && sykmeldtFnr != null) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
                Key.IDENTITETSNUMMER to innloggetFnr.toJson(),
                Key.UUID to transaksjonId.toJson()
            )
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
                Key.UUID to transaksjonId.toJson()
            )
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.FNR_LISTE to listOf(
                    sykmeldtFnr,
                    innloggetFnr
                ).toJson(Fnr.serializer()),
                Key.UUID to transaksjonId.toJson()
            )
        } else {
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.transaksjonId(transaksjonId)
            ) {
                "Mangler arbeidsgiverFnr eller arbeidstakerFnr."
                    .also {
                        sikkerLogger.error(it)
                        logger.error(it)
                    }

                onError(melding, melding.createFail("Ukjent feil oppstod", transaksjonId))
            }
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        if (step1Keys.all(melding::containsKey) && step2Keys.none(melding::containsKey)) {
            val arbeidsforholdListe = Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), melding)
            val orgrettigheter = Key.ORG_RETTIGHETER.les(String.serializer().set(), melding)

            val arbeidsgivere = trekkUtArbeidsforhold(arbeidsforholdListe, orgrettigheter)

            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.transaksjonId(transaksjonId)
            ) {
                if (orgrettigheter.isEmpty()) {
                    val feilmelding = "Må ha orgrettigheter for å kunne hente virksomheter."
                    onError(melding, melding.createFail(feilmelding, transaksjonId))
                } else if (arbeidsgivere.isEmpty()) {
                    val meldingMedDefault = melding.plus(Key.VIRKSOMHETER to emptyMap<String, String>().toJson())
                    finalize(meldingMedDefault)
                } else {
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.ORGNRUNDERENHETER to arbeidsgivere.toJson(String.serializer())
                    )
                }
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        val clientId = RedisKey.of(transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        val sykmeldtFnr = Key.FNR.les(Fnr.serializer(), melding)
        val innloggetFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)
        val virksomheter = Key.VIRKSOMHETER.les(
            MapSerializer(String.serializer(), String.serializer()),
            melding
        )
        val personer = Key.PERSONER.les(personMapSerializer, melding)
        val sykmeldtNavn = personer[sykmeldtFnr]?.navn.orEmpty()
        val avsenderNavn = personer[innloggetFnr]?.navn.orEmpty()

        if (clientId != null) {
            val gyldigeUnderenheter =
                virksomheter.map {
                    AktiveArbeidsgivere.Arbeidsgiver(
                        orgnrUnderenhet = it.key,
                        virksomhetsnavn = it.value
                    )
                }

            val gyldigResponse = ResultJson(
                success = AktiveArbeidsgivere(
                    fulltNavn = sykmeldtNavn,
                    avsenderNavn = avsenderNavn,
                    underenheter = gyldigeUnderenheter
                ).toJson(AktiveArbeidsgivere.serializer())
            )
                .toJson(ResultJson.serializer())

            RedisKey.of(clientId).write(gyldigResponse)
        } else {
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.transaksjonId(transaksjonId)
            ) {
                "Kunne ikke finne clientId for transaksjonId $transaksjonId i Redis!".also { feilmelding ->
                    sikkerLogger.error(feilmelding)
                    logger.error(feilmelding)
                }
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val clientId = RedisKey.of(fail.transaksjonId, event)
            .read()
            ?.let(UUID::fromString)

        if (clientId != null) {
            logger.error(fail.feilmelding)
            sikkerLogger.error(fail.feilmelding)

            val feilResponse = ResultJson(
                failure = fail.feilmelding.toJson()
            ).toJson(ResultJson.serializer())

            RedisKey.of(clientId).write(feilResponse)
        }
    }

    private fun Map<Key, JsonElement>.createFail(feilmelding: String, transaksjonId: UUID): Fail =
        Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = toJson()
        )

    private fun trekkUtArbeidsforhold(arbeidsforholdListe: List<Arbeidsforhold>, orgrettigheter: Set<String>): Set<String> =
        arbeidsforholdListe
            .mapNotNull { it.arbeidsgiver.organisasjonsnummer }
            .filter { it in orgrettigheter }
            .toSet()

    private fun RedisKey.read(): String? =
        redisStore.get(this)

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json.toString())
    }
}
