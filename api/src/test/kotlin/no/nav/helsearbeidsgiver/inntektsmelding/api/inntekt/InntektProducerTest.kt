package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import java.util.UUID

class InntektProducerTest : FunSpec({
    val testRapid = TestRapid()
    val inntektProducer = InntektProducer(testRapid)

    test("skal sende dato for inntekt i neste behov") {
        val expectedDate = 1.januar

        inntektProducer.publish(
            InntektRequest(UUID.randomUUID(), expectedDate)
        )

        val publisert = testRapid.firstMessage().fromJsonMapOnlyKeys()

        val forwardedDate = publisert[Key.BOOMERANG]
            ?.fromJsonMapOnlyKeys()
            ?.get(Key.INNTEKT_DATO)
            ?.fromJson(LocalDateSerializer)

        forwardedDate shouldBe expectedDate
    }
})
