package no.nav.hag.simba.utils.kontrakt.domene.forespoersel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespurtDataMedForrigeInntekt
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespurtDataMedTomtInntektForslag
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class ForespurtDataTest :
    FunSpec({
        listOf(
            row("forespurtData", ::mockForespurtData),
            row("forespurtDataMedTomtInntektForslag", ::mockForespurtDataMedTomtInntektForslag),
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
