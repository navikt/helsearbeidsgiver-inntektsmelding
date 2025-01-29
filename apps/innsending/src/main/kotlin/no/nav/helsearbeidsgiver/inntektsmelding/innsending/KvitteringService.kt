package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
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
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

private const val UKJENT_NAVN = "Ukjent navn"
private const val UKJENT_VIRKSOMHET = "Ukjent virksomhet"

class KvitteringService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed2Steg<KvitteringService.Steg0, KvitteringService.Steg1, KvitteringService.Steg2>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.KVITTERING_REQUESTED

    data class Steg0(
        val kontekstId: UUID,
        val forespoerselId: UUID,
    )

    data class Steg1(
        val forespoersel: Forespoersel,
    )

    sealed class Steg2 {
        data class Komplett(
            val orgnrMedNavn: Map<Orgnr, String>,
            val personer: Map<Fnr, Person>,
            val lagret: LagretInntektsmelding?,
        ) : Steg2()

        data object Delvis : Steg2()
    }

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 {
        val orgnrMedNavn = runCatching { Key.VIRKSOMHETER.les(orgMapSerializer, melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }
        val lagret =
            runCatching {
                Key.LAGRET_INNTEKTSMELDING
                    .les(ResultJson.serializer(), melding)
                    .success
                    ?.fromJson(LagretInntektsmelding.serializer())
            }

        val results = listOf(orgnrMedNavn, personer, lagret)

        return if (results.all { it.isSuccess }) {
            Steg2.Komplett(
                orgnrMedNavn = orgnrMedNavn.getOrThrow(),
                personer = personer.getOrThrow(),
                lagret = lagret.getOrThrow(),
            )
        } else if (results.any { it.isSuccess }) {
            Steg2.Delvis
        } else {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
    }

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                        Key.ORGNR_UNDERENHETER to setOf(steg1.forespoersel.orgnr).toJson(Orgnr.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }

        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                        Key.FNR_LISTE to setOf(steg1.forespoersel.fnr).toJson(Fnr.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }

        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_LAGRET_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_LAGRET_IM, it) }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        if (steg2 is Steg2.Komplett) {
            val sykmeldtNavn = steg2.personer[steg1.forespoersel.fnr]?.navn ?: UKJENT_NAVN
            val orgNavn = steg2.orgnrMedNavn[steg1.forespoersel.orgnr] ?: UKJENT_VIRKSOMHET

            val resultJson =
                ResultJson(
                    success =
                        KvitteringResultat(
                            forespoersel = steg1.forespoersel,
                            sykmeldtNavn = sykmeldtNavn,
                            orgNavn = orgNavn,
                            lagret = steg2.lagret,
                        ).toJson(KvitteringResultat.serializer()),
                )

            redisStore.skrivResultat(steg0.kontekstId, resultJson)
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.kontekstId(fail.kontekstId),
        ) {
            "Klarte ikke hente kvittering for forespørsel.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }

            val resultJson = ResultJson(failure = fail.feilmelding.toJson())

            redisStore.skrivResultat(fail.kontekstId, resultJson)
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@KvitteringService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
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
