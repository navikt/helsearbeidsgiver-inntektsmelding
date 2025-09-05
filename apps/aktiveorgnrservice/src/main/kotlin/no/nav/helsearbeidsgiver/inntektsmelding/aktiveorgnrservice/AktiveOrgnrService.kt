package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kontrakt.domene.arbeidsgiver.AktiveArbeidsgivere
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.Service
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val sykmeldtFnr: Fnr,
    val avsenderFnr: Fnr,
)

sealed class Steg1 {
    data class Komplett(
        val ansettelsesperioder: Map<Orgnr, Set<PeriodeAapen>>,
        val orgrettigheter: Set<Orgnr>,
        val personer: Map<Fnr, Person>,
    ) : Steg1()

    data object Delvis : Steg1()
}

data class Steg2(
    val virksomheter: Map<Orgnr, String>,
)

class AktiveOrgnrService(
    private val publisher: Publisher,
    override val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.AKTIVE_ORGNR_REQUESTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            sykmeldtFnr = Key.FNR.les(Fnr.serializer(), melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 {
        val ansettelsesperioder = runCatching { Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, melding) }
        val orgrettigheter = runCatching { Key.ORG_RETTIGHETER.les(Orgnr.serializer().set(), melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }

        val results = listOf(ansettelsesperioder, orgrettigheter, personer)

        return if (results.all { it.isSuccess }) {
            Steg1.Komplett(
                ansettelsesperioder = ansettelsesperioder.getOrThrow(),
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
            virksomheter = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val svarKafkaKey = KafkaKey(steg0.sykmeldtFnr)

        publisher.publish(
            key = steg0.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.ARBEIDSGIVER_FNR to steg0.avsenderFnr.toJson(),
                ).toJson(),
        )

        publisher.publish(
            key = steg0.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_ANSETTELSESPERIODER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to steg0.sykmeldtFnr.toJson(),
                ).toJson(),
        )

        publisher.publish(
            key = steg0.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
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
            val arbeidsgivere = steg1.ansettelsesperioder.keys.intersect(steg1.orgrettigheter)

            if (steg1.orgrettigheter.isEmpty()) {
                onError(steg0.kontekstId, "Må ha orgrettigheter for å kunne hente virksomheter.")
            } else if (arbeidsgivere.isEmpty()) {
                utfoerSteg2(data, steg0, steg1, Steg2(emptyMap()))
            } else {
                publisher.publish(
                    key = steg0.sykmeldtFnr,
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        data
                            .plus(
                                mapOf(
                                    Key.SVAR_KAFKA_KEY to KafkaKey(steg0.sykmeldtFnr).toJson(),
                                    Key.ORGNR_UNDERENHETER to arbeidsgivere.toJson(Orgnr.serializer()),
                                ),
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
            val sykmeldtNavn = steg1.personer[steg0.sykmeldtFnr]?.navn
            val avsenderNavn = steg1.personer[steg0.avsenderFnr]?.navn

            val aktiveArbeidgivere =
                steg2.virksomheter.map {
                    AktiveArbeidsgivere.Arbeidsgiver(
                        orgnr = it.key,
                        orgNavn = it.value,
                    )
                }

            val gyldigResponse =
                ResultJson(
                    success =
                        AktiveArbeidsgivere(
                            sykmeldtNavn = sykmeldtNavn,
                            avsenderNavn = avsenderNavn,
                            arbeidsgivere = aktiveArbeidgivere,
                        ).toJson(AktiveArbeidsgivere.serializer()),
                )

            redisStore.skrivResultat(steg0.kontekstId, gyldigResponse)
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
        kontekstId: UUID,
        feilmelding: String,
    ) {
        logger.error(feilmelding)
        sikkerLogger.error(feilmelding)

        val feilResponse = ResultJson(failure = feilmelding.toJson())

        redisStore.skrivResultat(kontekstId, feilResponse)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@AktiveOrgnrService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
        )
}
