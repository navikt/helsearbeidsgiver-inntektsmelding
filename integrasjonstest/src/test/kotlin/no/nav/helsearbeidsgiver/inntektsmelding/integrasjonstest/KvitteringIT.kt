package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
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
        imRepository.lagreInntektsmelding(forespoerselId, mockInntektsmelding())

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.LAGRET_INNTEKTSMELDING, nestedData = true)
            .filter(Key.EKSTERN_INNTEKTSMELDING, nestedData = true)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                // Skal finne inntektsmeldingdokumentet
                val imDokument = data[Key.LAGRET_INNTEKTSMELDING]

                imDokument.shouldNotBeNull()
                imDokument shouldNotBe Mock.tomResultJson

                data[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }

        redisConnection.get(RedisPrefix.Kvittering, transaksjonId).shouldNotBeNull()
    }

    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), Mock.orgnr)
        imRepository.lagreEksternInntektsmelding(forespoerselId, mockEksternInntektsmelding())

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.LAGRET_INNTEKTSMELDING, nestedData = true)
            .filter(Key.EKSTERN_INNTEKTSMELDING, nestedData = true)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                data[Key.LAGRET_INNTEKTSMELDING] shouldBe Mock.tomResultJson
                val eIm = data[Key.EKSTERN_INNTEKTSMELDING]
                eIm.shouldNotBeNull()
                eIm shouldNotBe Mock.tomResultJson
            }

        redisConnection.get(RedisPrefix.Kvittering, transaksjonId).shouldNotBeNull()
    }

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val transaksjonId = UUID.randomUUID()

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.LAGRET_INNTEKTSMELDING, nestedData = true)
            .filter(Key.EKSTERN_INNTEKTSMELDING, nestedData = true)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                // Skal ikke finne inntektsmeldingdokument
                data[Key.LAGRET_INNTEKTSMELDING] shouldBe Mock.tomResultJson
                data[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }
    }

    private object Mock {
        val orgnr = Orgnr.genererGyldig().verdi

        val tomResultJson = ResultJson(success = null).toJson(ResultJson.serializer())
    }
}
