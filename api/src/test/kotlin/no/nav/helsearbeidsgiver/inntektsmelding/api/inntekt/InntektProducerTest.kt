package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import java.time.LocalDate
import java.util.UUID

class InntektProducerTest : FunSpec({

    test("skal sende dato for inntekt i neste behov") {
        val testRapid = TestRapid()
        val inntektProducer = InntektProducer(testRapid)
        val dato = LocalDate.of(2020, 1, 1)
        inntektProducer.publish(InntektRequest(UUID.randomUUID(), dato))
        val request = testRapid.lastMessageJson().toJsonNode()
        println(request)
        val forwardedDate = request.get(Key.BOOMERANG.str).get(Key.INNTEKT_DATO.str).toJsonElement().fromJson(LocalDateSerializer)
        forwardedDate shouldBe dato
    }
})
