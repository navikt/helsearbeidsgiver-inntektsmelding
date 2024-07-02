package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.ktor.network.sockets.SocketTimeoutException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.util.UUID

val eksternInntektsmelding = EksternInntektsmelding(
    avsenderSystemNavn = "NAV_NO",
    avsenderSystemVersjon = "1.63",
    arkivreferanse = "im1234567",
    11.januar(2018).atStartOfDay()

)

class EksternInntektsmeldingLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val spinnKlient = mockk<SpinnKlient>()

    EksternInntektsmeldingLoeser(testRapid, spinnKlient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }
    every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternInntektsmelding

    test("Ved når inntektsmeldingId mangler skal feil publiseres") {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson()
        )

        val actual = testRapid.firstMessage().readFail()

        testRapid.inspektør.size shouldBeExactly 1
        Key.BEHOV.les(BehovType.serializer(), actual.utloesendeMelding.toMap()) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Mangler inntektsmeldingId"
    }

    test("Hvis inntektsmelding ikke finnes publiseres feil") {

        every { spinnKlient.hentEksternInntektsmelding(any()) } throws SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: 404")

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
            Key.SPINN_INNTEKTSMELDING_ID to UUID.randomUUID().toJson()
        )

        val actual = testRapid.firstMessage().readFail()

        testRapid.inspektør.size shouldBeExactly 1
        Key.BEHOV.les(BehovType.serializer(), actual.utloesendeMelding.toMap()) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Feil ved kall mot spinn api: $FIKK_SVAR_MED_RESPONSE_STATUS: 404"
    }

    test("Hvis Inntektsmelding finnes publiseres data") {
        every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternInntektsmelding

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
            Key.SPINN_INNTEKTSMELDING_ID to UUID.randomUUID().toJson()
        )

        val actual = testRapid.firstMessage().toMap()

        testRapid.inspektør.size shouldBeExactly 1

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), actual) shouldBe EventName.FORESPOERSEL_BESVART
        Key.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), actual) shouldBe eksternInntektsmelding
    }

    test("Hvis request timer ut blir feil publisert") {
        every { spinnKlient.hentEksternInntektsmelding(any()) } throws SocketTimeoutException("Timeout!")

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.name.toJson(),
            Key.SPINN_INNTEKTSMELDING_ID to UUID.randomUUID().toJson()
        )

        val actual = testRapid.firstMessage().readFail()

        testRapid.inspektør.size shouldBeExactly 1
        Key.BEHOV.les(BehovType.serializer(), actual.utloesendeMelding.toMap()) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        actual.feilmelding shouldBe "Ukjent feil ved kall til spinn"
    }
})
