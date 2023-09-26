package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

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

    test("'Map<Key, JsonElement>.toJson(): JsonElement' serialiserer korrekt") {
        val hitchhikersMapToTheGalaxy = mapOf(
            Key.BEHOV to "Hva er meningen med livet?".toJson(),
            Key.LØSNING to 42.toJson(Int.serializer())
        )

        val expected = hitchhikersMapToTheGalaxy.mapKeys { (key, _) -> key.str }.let(::JsonObject)

        val actual = hitchhikersMapToTheGalaxy.toJson()

        actual shouldBe expected
    }
})
