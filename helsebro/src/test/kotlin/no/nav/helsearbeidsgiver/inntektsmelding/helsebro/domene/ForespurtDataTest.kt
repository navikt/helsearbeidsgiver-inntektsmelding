package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.list
import no.nav.helsearbeidsgiver.felles.json.toJsonStr
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataListe
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedFastsattInntektListe

class ForespurtDataTest : FunSpec({
    listOf(
        row("forespurtDataListe", ::mockForespurtDataListe),
        row("forespurtDataMedFastsattInntektListe", ::mockForespurtDataMedFastsattInntektListe)
    )
        .forEach { (fileName, mockDataFn) ->
            val expectedJson = "json/$fileName.json".readResource().removeJsonWhitespace()

            test("Forespurt data serialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val serialisertJson = forespurtDataListe.toJsonStr(ForespurtData.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    serialisertJson shouldBe expectedJson
                }
            }

            test("Forespurt data deserialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val deserialisertJson = expectedJson.fromJson(ForespurtData.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    deserialisertJson shouldContainExactly forespurtDataListe
                }
            }
        }
})

private fun String.readResource(): String =
    ClassLoader.getSystemClassLoader()
        .getResource(this)
        ?.readText()!!
