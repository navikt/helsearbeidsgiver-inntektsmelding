package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainAll
import io.mockk.every
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

class InntektProducerTest : FunSpec({
    val testRapid = TestRapid()
    val inntektProducer = InntektProducer(testRapid)

    test("Publiserer melding på forventet format") {
        val expectedClientId = UUID.randomUUID()
        val request = InntektRequest(UUID.randomUUID(), 18.januar)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns expectedClientId

            inntektProducer.publish(request)
        }

        val publisert = testRapid.firstMessage().toMap()

        publisert shouldContainAll mapOf(
            Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
            Key.CLIENT_ID to expectedClientId.toJson(),
            Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
            Key.SKJAERINGSTIDSPUNKT to request.skjaeringstidspunkt.toJson()
        )
    }
})
