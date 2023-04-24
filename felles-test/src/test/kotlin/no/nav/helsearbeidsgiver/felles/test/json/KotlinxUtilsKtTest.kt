package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.Row2
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.list
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.json.toJson

class KotlinxUtilsKtTest : FunSpec({
    test("'JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement>' deserialiserer og filtrerer korrekt") {
        val broJson = """
            {
                "@behov": ["SPLEIS_BRO"],
                "@løsning": {
                    "førstevalg": "Marte",
                    "andrevalg": "Hege"
                },
                "ikkeEnBro": "Torten"
            }
        """.removeJsonWhitespace()

        val expectedMap = mapOf(
            Key.BEHOV to listOf("SPLEIS_BRO").toJson(String.serializer().list()),
            Key.LØSNING to mapOf(
                "førstevalg" to "Marte",
                "andrevalg" to "Hege"
            ).toJson(
                MapSerializer(
                    String.serializer(),
                    String.serializer()
                )
            )
        )

        val actualMap = broJson.parseJson().fromJsonMapOnlyKeys()

        actualMap shouldBe expectedMap
    }

    context("tryOrNull-hjelpefunksjon") {
        withData(
            @Suppress("RemoveExplicitTypeArguments")
            mapOf<_, Row2<() -> String, String?>>(
                "exception mappes til null" to row({ throw RuntimeException("\uD83D\uDC80") }, null),
                "verdi returneres" to row({ "\uD83D\uDC85" }, "\uD83D\uDC85")
            )
        ) { (block, expectedResult) ->
            tryOrNull(block) shouldBe expectedResult
        }
    }
})
