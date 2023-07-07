package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.rapidsrivers.fromJsonMapAllKeys
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.util.UUID

class InntektProducerTest : FunSpec({
    val testRapid = TestRapid()
    val inntektProducer = InntektProducer(testRapid)

    test("skal sende dato for inntekt i neste behov") {
        val expectedDate = 1.januar

        inntektProducer.publish(
            InntektRequest(UUID.randomUUID(), expectedDate)
        )

        val publisert = testRapid.firstMessage().fromJsonMapAllKeys()

        val forwardedDate = publisert[DataFelt.INNTEKT_DATO]?.fromJson(LocalDateSerializer)

        forwardedDate shouldBe expectedDate
    }
})
