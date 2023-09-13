package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonPrimitive
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument
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

        forespoerselRepository.lagreForespoersel(Mock.FORESPOERSEL_ID_GYLDIG, Mock.ORGNR)
        imRepository.lagreInntektsmelding(Mock.FORESPOERSEL_ID_GYLDIG, Mock.inntektsmeldingDokument)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to Mock.FORESPOERSEL_ID_GYLDIG.toJson()
        )

        Thread.sleep(5000)

        messages.filter(EventName.KVITTERING_REQUESTED)
            .filter(DataFelt.INNTEKTSMELDING_DOKUMENT)
            .first()
            .toMap()
            .also {
                // Skal finne inntektsmeldingdokumentet
                val imDokument = it[DataFelt.INNTEKTSMELDING_DOKUMENT]

                imDokument.shouldNotBeNull()
                imDokument shouldNotBe Mock.tomObjektStreng

                it[DataFelt.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomObjektStreng
            }

        redisStore.get(clientId.toString()).shouldNotBeNull()
    }


    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val clientId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(Mock.FORESPOERSEL_ID_GYLDIG, Mock.ORGNR)
        imRepository.lagreAvsenderSystemData(Mock.FORESPOERSEL_ID_GYLDIG, Mock.eksternInntektsmelding)

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to Mock.FORESPOERSEL_ID_GYLDIG.toJson()
        )

        Thread.sleep(5000)

        messages.filter(EventName.KVITTERING_REQUESTED)
            .filter(DataFelt.INNTEKTSMELDING_DOKUMENT)
            .first()
            .toMap()
            .also {
                it[DataFelt.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomObjektStreng
                val eIm = it[DataFelt.EKSTERN_INNTEKTSMELDING]
                eIm.shouldNotBeNull()
                eIm shouldNotBe Mock.tomObjektStreng
            }

        redisStore.get(clientId.toString()).shouldNotBeNull()
    }


    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val clientId = UUID.randomUUID()

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to Mock.FORESPOERSEL_ID_UGYLDIG.toJson()
        )

        Thread.sleep(5000)

        messages.filter(EventName.KVITTERING_REQUESTED)
            .filter(DataFelt.INNTEKTSMELDING_DOKUMENT)
            .first()
            .toMap()
            .also {
                // Skal ikke finne inntektsmeldingdokument - men en dummy payload
                it[DataFelt.INNTEKTSMELDING_DOKUMENT] shouldBe Mock.tomObjektStreng
                it[DataFelt.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomObjektStreng
            }
    }

    private object Mock {
        const val ORGNR = "987"
        const val FORESPOERSEL_ID_GYLDIG = "gyldig-forespørsel"
        const val FORESPOERSEL_ID_UGYLDIG = "ugyldig-forespørsel"

        val inntektsmeldingDokument = mockInntektsmeldingDokument()
        val eksternInntektsmelding = EksternInntektsmelding(
            "AltinnPortal",
            "1.489",
            "AR123456",
            11.januar.atStartOfDay()
        )

        /** Rar verdi. Tror denne bør fikses i prodkoden. */
        val tomObjektStreng = JsonPrimitive("{}")
    }
}
