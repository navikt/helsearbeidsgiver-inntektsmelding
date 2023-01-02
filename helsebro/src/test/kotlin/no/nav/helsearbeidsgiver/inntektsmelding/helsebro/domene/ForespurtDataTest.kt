package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.mockForespurtDataListe
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.mockForespurtDataMedFastsattInntektListe

class ForespurtDataTest : FunSpec({
    listOf(
        row("forespurtDataListe", ::mockForespurtDataListe),
        row("forespurtDataMedFastsattInntektListe", ::mockForespurtDataMedFastsattInntektListe)
    )
        .forEach { (fileName, mockDataFn) ->
            val expectedJson = "json/$fileName.json".readResource().removeJsonWhitespace()

            test("Forespurt data serialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val serialisertJson = Json.encodeToString(forespurtDataListe)

                withClue("Validerer mot '$fileName'") {
                    serialisertJson shouldBeEqualComparingTo expectedJson
                }
            }

            test("Forespurt data deserialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val deserialisertJson = Json.decodeFromString<List<ForespurtData>>(expectedJson)

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
