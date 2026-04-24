package no.nav.helsearbeidsgiver.inntektsmelding.faisuservice

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.ansettelsesforholdSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.felles.utils.overlapperMed
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import kotlin.collections.orEmpty

data class Steg0(
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val ansettelsesforhold: Map<Orgnr, Set<Ansettelsesforhold>>,
)

class HentArbeidsforholdService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val initialEventName = EventName.HENT_ARBEIDSFORHOLD_REQUESTED
    override val serviceEventName = EventName.SERVICE_HENT_ARBEIDSFORHOLD

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            ansettelsesforhold = Key.ANSETTELSESFORHOLD.les(ansettelsesforholdSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to serviceEventName.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val svarKafkaKey = KafkaKey(steg0.forespoerselId)

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to serviceEventName.toJson(),
            Key.BEHOV to BehovType.HENT_ANSETTELSESPERIODER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to steg1.forespoersel.fnr.toJson(),
                ).toJson(),
        )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val forespoersel = steg1.forespoersel

        val ansettelsesforholdForAktuellOrg = steg2.ansettelsesforhold[forespoersel.orgnr].orEmpty()

        // Dette er en forenklet metode
        // TODO: Finne ut hvordan vi skal filtrere ut urelevante ansettelseperioder på en bedre måte
        val ansettelsesforholdMedSykmeldingOverlapp =
            ansettelsesforholdForAktuellOrg.filter { forhold ->
                forespoersel.sykmeldingsperioder.any { sykmeldingPeriode ->
                    sykmeldingPeriode.overlapperMed(forhold)
                }
            }

        val resultJson =
            ResultJson(
                success = ansettelsesforholdMedSykmeldingOverlapp.toJson(Ansettelsesforhold.serializer()),
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
            Log.klasse(this@HentArbeidsforholdService),
            Log.event(serviceEventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
