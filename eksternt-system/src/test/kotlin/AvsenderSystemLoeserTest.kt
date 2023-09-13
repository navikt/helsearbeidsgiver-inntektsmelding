import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.ktor.network.sockets.SocketTimeoutException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.eksterntsystem.EksternInntektsmeldingLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.eksterntsystem.spinn.FIKK_SVAR_MED_RESPONSE_STATUS
import no.nav.helsearbeidsgiver.inntektsmelding.eksterntsystem.spinn.SpinnApiException
import no.nav.helsearbeidsgiver.inntektsmelding.eksterntsystem.spinn.SpinnKlient
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

val eksternInntektsmelding = EksternInntektsmelding(
    avsenderSystemNavn = "NAV_NO",
    avsenderSystemVersjon = "1.63",
    arkivreferanse = "im1234567",
    11.januar(2018).atStartOfDay()

)

class AvsenderSystemLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val spinnKlient = mockk<SpinnKlient>()

    EksternInntektsmeldingLoeser(testRapid, spinnKlient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }
    every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternInntektsmelding

    test("Ved når inntektsmeldingId mangler skal feil publiseres") {

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson()
            )
        }

        val actual = testRapid.firstMessage().toDomeneMessage<Fail>()

        testRapid.inspektør.size shouldBeExactly 1
        actual.behov shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Mangler inntektsmeldingId"
    }

    test("Hvis inntektsmelding ikke finnes publiseres feil") {

        every { spinnKlient.hentEksternInntektsmelding(any()) } throws SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: 404")

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
                DataFelt.SPINN_INNTEKTSMELDING_ID to randomUuid().toJson()
            )
        }

        val actual = testRapid.firstMessage().toDomeneMessage<Fail>()

        testRapid.inspektør.size shouldBeExactly 1
        actual.behov shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Feil ved kall mot spinn api: $FIKK_SVAR_MED_RESPONSE_STATUS: 404"
    }

    test("Hvis Inntektsmelding finnes publiseres data") {
        every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternInntektsmelding

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
                DataFelt.SPINN_INNTEKTSMELDING_ID to randomUuid().toJson()
            )
        }

        val actual = testRapid.firstMessage().toDomeneMessage<Data>() {
            it.interestedIn(DataFelt.EKSTERN_INNTEKTSMELDING.str)
        }

        testRapid.inspektør.size shouldBeExactly 1
        actual.event shouldBe EventName.FORESPOERSEL_BESVART

        actual[DataFelt.EKSTERN_INNTEKTSMELDING].toString().fromJson(EksternInntektsmelding.serializer()) shouldBe eksternInntektsmelding
    }

    test("Hvis request timer ut blir feil publisert") {
        every { spinnKlient.hentEksternInntektsmelding(any()) } throws SocketTimeoutException("Timeout!")

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
                DataFelt.SPINN_INNTEKTSMELDING_ID to randomUuid().toJson()
            )
        }

        val actual = testRapid.firstMessage().toDomeneMessage<Fail>()

        testRapid.inspektør.size shouldBeExactly 1
        actual.behov shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Ukjent feil ved kall til spinn"
    }
})
