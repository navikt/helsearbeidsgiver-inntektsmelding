package no.nav.helsearbeidsgiver.felles

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedFastsattInntekt
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedForrigeInntekt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class ForespurtDataTest :
    FunSpec({
        listOf(
            row("forespurtData", ::mockForespurtData),
            row("forespurtDataMedFastsattInntekt", ::mockForespurtDataMedFastsattInntekt),
            row("forespurtDataMedForrigeInntekt", ::mockForespurtDataMedForrigeInntekt),
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
