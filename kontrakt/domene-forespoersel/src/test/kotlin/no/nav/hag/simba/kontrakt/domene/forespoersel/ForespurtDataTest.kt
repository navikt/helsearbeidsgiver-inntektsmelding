package no.nav.hag.simba.kontrakt.domene.forespoersel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtDataMedForrigeInntekt
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtDataMedTomtInntektForslag
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class ForespurtDataTest :
    FunSpec({
        listOf(
            "forespurtData" to ::mockForespurtData,
            "forespurtDataMedTomtInntektForslag" to ::mockForespurtDataMedTomtInntektForslag,
            "forespurtDataMedForrigeInntekt" to ::mockForespurtDataMedForrigeInntekt,
        ).forEach { (fileName, mockDataFn) ->
            val expectedJson = "json/$fileName.json".readResource().removeJsonWhitespace()

            test("Forespurt data serialiseres korrekt") {
                val forespurtData = mockDataFn()

                val serialisertJson = forespurtData.toJsonStr(ForespurtData.serializer())

                withClue("Validerer mot '$fileName'") {
                    serialisertJson shouldBe expectedJson
                }
            }

            test("Forespurt data deserialiseres korrekt") {
                val forespurtData = mockDataFn()

                val deserialisertJson = expectedJson.fromJson(ForespurtData.serializer())

                withClue("Validerer mot '$fileName'") {
                    deserialisertJson shouldBe forespurtData
                }
            }
        }
    })
