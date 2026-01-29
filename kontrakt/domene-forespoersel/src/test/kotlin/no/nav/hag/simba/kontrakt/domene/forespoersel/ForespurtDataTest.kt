package no.nav.hag.simba.kontrakt.domene.forespoersel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class ForespurtDataTest :
    FunSpec({
        val expectedJson = "json/forespurtData.json".readResource().removeJsonWhitespace()

        test("Forespurt data serialiseres korrekt") {
            val forespurtData = mockForespurtData()

            val serialisertJson = forespurtData.toJson(ForespurtData.serializer()).toString()

            serialisertJson shouldBe expectedJson
        }

        test("Forespurt data deserialiseres korrekt") {
            val forespurtData = mockForespurtData()

            val deserialisertJson = expectedJson.fromJson(ForespurtData.serializer())

            deserialisertJson shouldBe forespurtData
        }
    })
