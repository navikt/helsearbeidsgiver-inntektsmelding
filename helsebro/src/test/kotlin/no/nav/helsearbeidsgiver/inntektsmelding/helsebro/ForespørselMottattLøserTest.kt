package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.test.json.tryToJson
import java.util.UUID

class ForespørselMottattLøserTest : FunSpec({

    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    ForespørselMottattLøser(testRapid, mockPriProducer)

    test("Løser mottar melding om mottatt forespørsel") {
        val expectedTrengerForespørsel = TrengerForespørsel(
            "123",
            "abc",
            UUID.randomUUID()
        )

        testRapid.sendJson(
            "eventType" to "FORESPØRSEL_MOTTATT".tryToJson(),
            "orgnr" to expectedTrengerForespørsel.orgnr.tryToJson(),
            "fnr" to expectedTrengerForespørsel.fnr.tryToJson(),
            "vedtaksperiodeId" to expectedTrengerForespørsel.vedtaksperiodeId.toString().tryToJson()
        )

        verifySequence {
            mockPriProducer.send(expectedTrengerForespørsel)
        }
    }
})
