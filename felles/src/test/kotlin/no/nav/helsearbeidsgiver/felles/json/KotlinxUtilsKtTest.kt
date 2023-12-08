package no.nav.helsearbeidsgiver.felles.json

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.Row2
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

class KotlinxUtilsKtTest : FunSpec({

    context("toJsonElement") {
        test("konverterer korrekt fra JsonNode til JsonElement") {
            val bilboJson = """
                {
                    "name": {
                        "first": "Bilbo",
                        "last": "Baggins"
                    },
                    "age": 111
                }
            """.removeJsonWhitespace()

            val bilboJsonNode = jacksonOm.readTree(bilboJson)

            shouldNotThrowAny {
                val converted = bilboJsonNode.toJsonElement()

                converted.jsonObject.let { hobbit ->
                    hobbit["name"].shouldNotBeNull().jsonObject.let { name ->
                        name["first"].shouldNotBeNull().jsonPrimitive.content shouldBe "Bilbo"
                        name["last"].shouldNotBeNull().jsonPrimitive.content shouldBe "Baggins"
                    }
                    hobbit["age"].shouldNotBeNull().jsonPrimitive.content shouldBe "111"
                }
            }
        }
    }

    context("toMap") {
        withData(
            mapOf<String, Map<IKey, String>>(
                "inneholder bare Key" to mapOf(Key.EVENT_NAME to "testevent"),
                "inneholder bare Pri.Key" to mapOf(Pri.Key.NOTIS to "husk å drikke vann"),
                "inneholder Key og Pri.Key" to mapOf(
                    Key.EVENT_NAME to "testevent",
                    Pri.Key.NOTIS to "husk å drikke vann"
                )
            )
        ) { expectedMap ->
            val json = JsonObject(
                expectedMap.mapKeys { it.key.str }
                    .mapValues { it.value.toJson() }
            )

            val jsonMap = json.toMap()

            jsonMap shouldBe expectedMap.mapValues { it.value.toJson() }
        }
    }

    context("les") {
        withData(
            mapOf<String, Row2<IKey, String>>(
                "Key leses" to row(Key.EVENT_NAME, "testevent"),
                "Pri.Key leses" to row(Pri.Key.NOTIS, "husk å drikke vann")
            )
        ) { (key, expectedValue) ->
            val jsonMap = mapOf(key to expectedValue.toJson())

            val actualValue = key.les(String.serializer(), jsonMap)

            actualValue shouldBe expectedValue
        }

        test("Key og Pri.Key leses fra samme map") {
            val jsonMap = mapOf(
                Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer())
            )

            Key.EVENT_NAME.les(EventName.serializer(), jsonMap) shouldBe EventName.TRENGER_REQUESTED
            Pri.Key.NOTIS.les(Pri.NotisType.serializer(), jsonMap) shouldBe Pri.NotisType.FORESPØRSEL_MOTTATT
        }

        withData(
            mapOf(
                "gir IllegalArgumentException dersom nøkkel ikke finnes" to emptyMap(),
                "gir IllegalArgumentException dersom nøkkel finnes, men verdi er null-json" to mapOf(Key.EVENT_NAME to JsonNull)
            )
        ) { jsonMap ->
            val e = shouldThrowExactly<IllegalArgumentException> {
                Key.EVENT_NAME.les(EventName.serializer(), jsonMap)
            }

            e.message shouldBe "Felt '${Key.EVENT_NAME}' mangler i JSON-map."
        }
    }
})
