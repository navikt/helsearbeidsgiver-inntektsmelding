package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class Steg0(
    val transaksjonId: UUID,
    val sykmeldtFnr: Fnr,
    val avsenderFnr: Fnr,
)

sealed class Steg1 {
    data class Komplett(
        val arbeidsforhold: List<Arbeidsforhold>,
        val orgrettigheter: Set<String>,
        val personer: Map<Fnr, Person>,
    ) : Steg1()

    data object Delvis : Steg1()
}

data class Steg2(
    val virksomheter: Map<String, String>,
)

class AktiveOrgnrService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.AKTIVE_ORGNR_REQUESTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            sykmeldtFnr = Key.FNR.les(Fnr.serializer(), melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 {
        val arbeidsforhold = runCatching { Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), melding) }
        val orgrettigheter = runCatching { Key.ORG_RETTIGHETER.les(String.serializer().set(), melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }

        val results = listOf(arbeidsforhold, orgrettigheter, personer)

        return if (results.all { it.isSuccess }) {
            Steg1.Komplett(
                arbeidsforhold = arbeidsforhold.getOrThrow(),
                orgrettigheter = orgrettigheter.getOrThrow(),
                personer = personer.getOrThrow(),
            )
        } else if (results.any { it.isSuccess }) {
            Steg1.Delvis
        } else {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
    }

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            virksomheter = Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
            Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to steg0.avsenderFnr.toJson(),
                ).toJson(),
        )

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_ARBEIDSFORHOLD.toJson(),
            Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to steg0.sykmeldtFnr.toJson(),
                ).toJson(),
        )

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR_LISTE to
                        setOf(
                            steg0.sykmeldtFnr,
                            steg0.avsenderFnr,
                        ).toJson(Fnr.serializer()),
                ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (steg1 is Steg1.Komplett) {
            val arbeidsgivere = trekkUtArbeidsforhold(steg1.arbeidsforhold, steg1.orgrettigheter)

            if (steg1.orgrettigheter.isEmpty()) {
                onError(steg0.transaksjonId, "Må ha orgrettigheter for å kunne hente virksomheter.")
            } else if (arbeidsgivere.isEmpty()) {
                utfoerSteg2(data, steg0, steg1, Steg2(emptyMap()))
            } else {
                rapid.publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                    Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.ORGNR_UNDERENHETER to arbeidsgivere.toJson(String.serializer()),
                        ).toJson(),
                )
            }
        }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        if (steg1 is Steg1.Komplett) {
            val sykmeldtNavn = steg1.personer[steg0.sykmeldtFnr]?.navn.orEmpty()
            val avsenderNavn = steg1.personer[steg0.avsenderFnr]?.navn.orEmpty()

            val gyldigeUnderenheter =
                steg2.virksomheter.map {
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
                )

            redisStore.skrivResultat(steg0.transaksjonId, gyldigResponse)
        } else {
            "Steg 1 er ikke komplett under utførelse av steg 2.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        onError(fail.kontekstId, fail.feilmelding)
    }

    private fun onError(
        transaksjonId: UUID,
        feilmelding: String,
    ) {
        logger.error(feilmelding)
        sikkerLogger.error(feilmelding)

        val feilResponse = ResultJson(failure = feilmelding.toJson())

        redisStore.skrivResultat(transaksjonId, feilResponse)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@AktiveOrgnrService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
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
