package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.AvsenderSystemData
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.FIKK_SVAR_MED_RESPONSE_STATUS
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.SpinnApiException
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.SpinnKlient
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.*

val avsenderSystemData = AvsenderSystemData(
    avsenderSystemNavn = "NAV_NO",
    avsenderSystemVersjon = "1.63",
    arkivreferanse = "im1234567"
)

class AvsenderSystemLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val spinnKlient = mockk<SpinnKlient>()

    AvsenderSystemLoeser(testRapid, spinnKlient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }
    every { spinnKlient.hentAvsenderSystemData(any()) } returns avsenderSystemData

    test("Ved når inntektsmeldingId mangler skal feil publiseres") {

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_AVSENDER_SYSTEM.name.toJson()
            )
        }

        val actual = testRapid.firstMessage().toJsonNode().toDomeneMessage<Fail>()

        testRapid.inspektør.size shouldBeExactly 1
        actual.behov shouldBe BehovType.HENT_AVSENDER_SYSTEM
        actual.feilmelding shouldBe "Mangler inntektsmeldingId"
    }

    test("Hvis inntektsmelding ikke finnes publiseres feil") {

        every { spinnKlient.hentAvsenderSystemData(any()) } throws SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: 404")

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_AVSENDER_SYSTEM.name.toJson(),
                DataFelt.SPINN_INNTEKTSMELDING_ID to randomUuid().toJson()
            )
        }

        val actual = testRapid.firstMessage().toJsonNode().toDomeneMessage<Fail>()

        testRapid.inspektør.size shouldBeExactly 1
        actual.behov shouldBe BehovType.HENT_AVSENDER_SYSTEM
        actual.feilmelding shouldBe "Feil ved kall mot spinn api: $FIKK_SVAR_MED_RESPONSE_STATUS: 404"
    }

    test("Hvis Inntektsmelding finnes publiseres data") {
        every { spinnKlient.hentAvsenderSystemData(any()) } returns avsenderSystemData

        mockStatic(::randomUuid) {
            every { randomUuid() } returns UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.BEHOV to BehovType.HENT_AVSENDER_SYSTEM.name.toJson(),
                DataFelt.SPINN_INNTEKTSMELDING_ID to randomUuid().toJson()
            )
        }

        val actual = testRapid.firstMessage().toJsonNode().toDomeneMessage<Data>() {
            it.interestedIn(DataFelt.AVSENDER_SYSTEM_DATA.str)
        }

        testRapid.inspektør.size shouldBeExactly 1
        actual.event shouldBe EventName.FORESPOERSEL_BESVART
        actual[DataFelt.AVSENDER_SYSTEM_DATA]["avsenderSystemNavn"].get("content").asText() shouldBe avsenderSystemData.avsenderSystemNavn
        actual[DataFelt.AVSENDER_SYSTEM_DATA]["avsenderSystemVersjon"].get("content").asText() shouldBe avsenderSystemData.avsenderSystemVersjon
        actual[DataFelt.AVSENDER_SYSTEM_DATA]["arkivreferanse"].get("content").asText() shouldBe avsenderSystemData.arkivreferanse
    }
})
