package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import java.time.LocalDate
import java.util.UUID

class InntektProducerTest : FunSpec({

    test("skal sende dato for inntekt i neste behov") {
        val testRapid = TestRapid()
        val inntektProducer = InntektProducer(testRapid)
        val forespoerselId = UUID.randomUUID()
        val dato = LocalDate.of(2020, 1, 1)
        inntektProducer.publish(InntektRequest(forespoerselId, dato))
        val jsonElement = testRapid.firstMessage().jsonObject[Key.BOOMERANG.str]
        val forwardedDate = jsonElement
            ?.jsonObject?.get(Key.INNTEKT_DATO.str)
            ?.fromJson(LocalDateSerializer)
        forwardedDate shouldBe dato
    }
})
