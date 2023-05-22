package no.nav.helsearbeidsgiver.felles.json

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.removeJsonWhitespace
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

            val bilboJsonNode = customObjectMapper().readTree(bilboJson)

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
})
