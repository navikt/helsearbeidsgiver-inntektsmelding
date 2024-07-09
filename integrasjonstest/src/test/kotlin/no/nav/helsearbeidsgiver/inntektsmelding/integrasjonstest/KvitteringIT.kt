package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonPrimitive
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
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
        val clientId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), Mock.ORGNR)
        imRepository.lagreInntektsmelding(forespoerselId.toString(), Mock.inntektsmeldingDokument)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
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
                imDokument shouldNotBe Mock.tomObjektStreng

                it[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomObjektStreng
            }

        redisStore.get(RedisKey.of(clientId)).shouldNotBeNull()
    }

    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val clientId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), Mock.ORGNR)
        imRepository.lagreEksternInntektsmelding(forespoerselId.toString(), Mock.eksternInntektsmelding)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                it[Key.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomObjektStreng
                val eIm = it[Key.EKSTERN_INNTEKTSMELDING]
                eIm.shouldNotBeNull()
                eIm shouldNotBe Mock.tomObjektStreng
            }

        redisStore.get(RedisKey.of(clientId)).shouldNotBeNull()
    }

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val clientId = UUID.randomUUID()

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                // Skal ikke finne inntektsmeldingdokument - men en dummy payload
                it[Key.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomObjektStreng
                it[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomObjektStreng
            }
    }

    private object Mock {
        const val ORGNR = "987"

        val inntektsmeldingDokument = mockInntektsmelding()
        val eksternInntektsmelding =
            EksternInntektsmelding(
                "AltinnPortal",
                "1.489",
                "AR123456",
                11.januar.atStartOfDay(),
            )

        /** Rar verdi. Tror denne bør fikses i prodkoden. */
        val tomObjektStreng = JsonPrimitive("{}")
    }
}
