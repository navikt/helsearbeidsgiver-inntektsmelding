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
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

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

            val bilboJsonNode = Jackson.objectMapper.readTree(bilboJson)

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

    context("løsning") {
        val loesning = "gninsøl".toLøsningSuccess()
        val loesningJson = """
            {
                "løsningType": "SUCCESS",
                "resultat": "gninsøl"
            }
        """.removeJsonWhitespace()

        val testSerializer = String.serializer().løsning()

        test("serialiserer korrekt") {
            loesning.toJsonStr(testSerializer) shouldBe loesningJson
        }

        test("deserialiserer korrekt") {
            loesningJson.fromJson(testSerializer) shouldBe loesning
        }
    }

    context("toMap") {
        withData(
            mapOf<String, Map<IKey, String>>(
                "inneholder bare Key" to mapOf(Key.EVENT_NAME to "testevent"),
                "inneholder bare DataFelt" to mapOf(DataFelt.TRENGER_INNTEKT to "masse info"),
                "inneholder bare Pri.Key" to mapOf(Pri.Key.NOTIS to "husk å drikke vann"),
                "inneholder Key, DataFelt og Pri.Key" to mapOf(
                    Key.EVENT_NAME to "testevent",
                    DataFelt.TRENGER_INNTEKT to "masse info",
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
                "DataFelt leses" to row(DataFelt.TRENGER_INNTEKT, "masse info"),
                "Pri.Key leses" to row(Pri.Key.NOTIS, "husk å drikke vann")
            )
        ) { (key, expectedValue) ->
            val jsonMap = mapOf(key to expectedValue.toJson())

            val actualValue = key.les(String.serializer(), jsonMap)

            actualValue shouldBe expectedValue
        }

        test("Key, DataFelt og Pri.Key leses fra samme map") {
            val jsonMap = mapOf(
                Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                DataFelt.TRENGER_INNTEKT to mockTrengerInntekt().toJson(TrengerInntekt.serializer()),
                Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer())
            )

            Key.EVENT_NAME.les(EventName.serializer(), jsonMap) shouldBe EventName.TRENGER_REQUESTED
            DataFelt.TRENGER_INNTEKT.les(TrengerInntekt.serializer(), jsonMap) shouldBe mockTrengerInntekt()
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
