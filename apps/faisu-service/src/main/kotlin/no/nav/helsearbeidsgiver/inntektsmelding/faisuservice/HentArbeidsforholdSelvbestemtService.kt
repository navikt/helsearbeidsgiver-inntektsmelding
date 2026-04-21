package no.nav.helsearbeidsgiver.inntektsmelding.faisuservice

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.felles.utils.overlapperMed
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed1Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import kotlin.collections.orEmpty

class HentArbeidsforholdSelvbestemtService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed1Steg<HentArbeidsforholdSelvbestemtService.Steg0, HentArbeidsforholdSelvbestemtService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val initialEventName = EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED
    override val serviceEventName = EventName.SERVICE_HENT_ARBEIDSFORHOLD_SELVBESTEMT

    data class Steg0(
        val kontekstId: UUID,
        val orgnr: Orgnr,
        val sykmeldFnr: Fnr,
        val periode: Periode,
    )

    data class Steg1(
        val ansettelsesperioder: Map<Orgnr, Set<PeriodeAapen>>,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), melding),
            sykmeldFnr = Key.SYKMELDT_FNR.les(Fnr.serializer(), melding),
            periode = Key.PERIODE.les(Periode.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            ansettelsesperioder = Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val svarKafkaKey = KafkaKey(steg0.sykmeldFnr)

        publisher.publish(
            key = steg0.sykmeldFnr,
            Key.EVENT_NAME to serviceEventName.toJson(),
            Key.BEHOV to BehovType.HENT_ANSETTELSESPERIODER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to steg0.sykmeldFnr.toJson(),
                ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val relevanteAnsettelsesperioder =
            steg1.ansettelsesperioder[steg0.orgnr]
                .orEmpty()
                .filter { steg0.periode.overlapperMed(it) }
                .toSet()

        val resultJson =
            ResultJson(
                success = relevanteAnsettelsesperioder.toJson(PeriodeAapen.serializer()),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val resultJson = ResultJson(failure = fail.feilmelding.toJson())
        redisStore.skrivResultat(fail.kontekstId, resultJson)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentArbeidsforholdSelvbestemtService),
            Log.event(serviceEventName),
            Log.kontekstId(kontekstId),
        )
}
