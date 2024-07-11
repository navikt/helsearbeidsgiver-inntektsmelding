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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
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
    override val redisStore: RedisStoreClassSpecific,
) : Service() {
    private val sikkerLogger = sikkerLogger()
    private val logger = logger()

    override val eventName = EventName.AKTIVE_ORGNR_REQUESTED
    override val startKeys =
        setOf(
            Key.FNR,
            Key.ARBEIDSGIVER_FNR,
        )
    override val dataKeys =
        setOf(
            Key.ORG_RETTIGHETER,
            Key.ARBEIDSFORHOLD,
            Key.PERSONER,
            Key.VIRKSOMHETER,
        )

    private val step1Keys =
        setOf(
            Key.ORG_RETTIGHETER,
            Key.ARBEIDSFORHOLD,
            Key.PERSONER,
        )
    private val step2Keys =
        setOf(
            Key.VIRKSOMHETER,
        )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val sykmeldtFnr = Key.FNR.les(Fnr.serializer(), melding)
        val innloggetFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding)

        if (isFinished(melding)) {
            val virksomheter =
                Key.VIRKSOMHETER.les(
                    MapSerializer(String.serializer(), String.serializer()),
                    melding,
                )
            val personer = Key.PERSONER.les(personMapSerializer, melding)
            val sykmeldtNavn = personer[sykmeldtFnr]?.navn.orEmpty()
            val avsenderNavn = personer[innloggetFnr]?.navn.orEmpty()

            val gyldigeUnderenheter =
                virksomheter.map {
                    AktiveArbeidsgivere.Arbeidsgiver(
                        orgnrUnderenhet = it.key,
                        virksomhetsnavn = it.value,
                    )
                }

            val gyldigResponse =
                ResultJson(
                    success =
                        AktiveArbeidsgivere(
                            fulltNavn = sykmeldtNavn,
                            avsenderNavn = avsenderNavn,
                            underenheter = gyldigeUnderenheter,
                        ).toJson(AktiveArbeidsgivere.serializer()),
                ).toJson(ResultJson.serializer())

            redisStore.set(RedisKey.of(transaksjonId), gyldigResponse)
        } else if (step1Keys.all(melding::containsKey) && step2Keys.none(melding::containsKey)) {
            val arbeidsforholdListe = Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), melding)
            val orgrettigheter = Key.ORG_RETTIGHETER.les(String.serializer().set(), melding)

            val arbeidsgivere = trekkUtArbeidsforhold(arbeidsforholdListe, orgrettigheter)

            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(eventName),
                Log.transaksjonId(transaksjonId),
            ) {
                if (orgrettigheter.isEmpty()) {
                    val feilmelding = "Må ha orgrettigheter for å kunne hente virksomheter."
                    onError(melding, melding.createFail(feilmelding, transaksjonId))
                } else if (arbeidsgivere.isEmpty()) {
                    val meldingMedDefault = melding.plus(Key.VIRKSOMHETER to emptyMap<String, String>().toJson())
                    onData(meldingMedDefault)
                } else {
                    rapid.publish(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.ORGNRUNDERENHETER to arbeidsgivere.toJson(String.serializer()),
                    )
                }
            }
        } else if (dataKeys.none(melding::containsKey)) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ARBEIDSGIVER_FNR to innloggetFnr.toJson(),
                    ).toJson(),
            )
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
            )
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FNR_LISTE to
                    listOf(
                        sykmeldtFnr,
                        innloggetFnr,
                    ).toJson(Fnr.serializer()),
            )
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding)

        val feilResponse =
            ResultJson(
                failure = fail.feilmelding.toJson(),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(fail.transaksjonId), feilResponse)
    }

    private fun Map<Key, JsonElement>.createFail(
        feilmelding: String,
        transaksjonId: UUID,
    ): Fail =
        Fail(
            feilmelding = feilmelding,
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = toJson(),
        )

    private fun trekkUtArbeidsforhold(
        arbeidsforholdListe: List<Arbeidsforhold>,
        orgrettigheter: Set<String>,
    ): Set<String> =
        arbeidsforholdListe
            .mapNotNull { it.arbeidsgiver.organisasjonsnummer }
            .filter { it in orgrettigheter }
            .toSet()
}
