package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KvitteringIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal hente data til kvittering`() {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), Mock.orgnr)
        imRepository.lagreInntektsmelding(forespoerselId.toString(), Mock.inntektsmeldingDokument)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                // Skal finne inntektsmeldingdokumentet
                val imDokument = it[Key.INNTEKTSMELDING_DOKUMENT]

                imDokument.shouldNotBeNull()
                imDokument shouldNotBe Mock.tomResultJson

                it[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }

        redisStore.get(RedisKey.of(transaksjonId)).shouldNotBeNull()
    }

    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), Mock.orgnr)
        imRepository.lagreEksternInntektsmelding(forespoerselId.toString(), Mock.eksternInntektsmelding)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                it[Key.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomResultJson
                val eIm = it[Key.EKSTERN_INNTEKTSMELDING]
                eIm.shouldNotBeNull()
                eIm shouldNotBe Mock.tomResultJson
            }

        redisStore.get(RedisKey.of(transaksjonId)).shouldNotBeNull()
    }

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val transaksjonId = UUID.randomUUID()

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                // Skal ikke finne inntektsmeldingdokument
                it[Key.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomResultJson
                it[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }
    }

    private object Mock {
        val orgnr = Orgnr.genererGyldig().verdi
        val inntektsmeldingDokument = mockInntektsmelding()
        val eksternInntektsmelding = mockEksternInntektsmelding()

        val tomResultJson = ResultJson(success = null).toJson(ResultJson.serializer())
    }
}
