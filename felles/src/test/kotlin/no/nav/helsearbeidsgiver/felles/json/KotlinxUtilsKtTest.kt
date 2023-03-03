package no.nav.helsearbeidsgiver.felles.json

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid

class KotlinxUtilsKtTest : FunSpec({
    test("'T.toJson(KSerializer<T>): JsonElement' serialiserer korrekt") {
        val samwise = Hobbit(
            name = Name("Samwise", "Gamgee"),
            age = 38
        )

        val expectedJson = """
            {
                "name": {
                    "first": "Samwise",
                    "last": "Gamgee"
                },
                "age": 38
            }
        """.removeJsonWhitespace()

        val actualJson = samwise.toJson(Hobbit.serializer()).toString()

        actualJson shouldBe expectedJson
    }

    test("'T.toJsonStr(KSerializer<T>): String' serialiserer korrekt") {
        val samwise = Hobbit(
            name = Name("Frodo", "Baggins"),
            age = 50
        )

        val expectedJson = """
            {
                "name": {
                    "first": "Frodo",
                    "last": "Baggins"
                },
                "age": 50
            }
        """.removeJsonWhitespace()

        val actualJson = samwise.toJsonStr(Hobbit.serializer())

        actualJson shouldBe expectedJson
    }

    test("'List<T>.toJson(KSerializer<T>): JsonElement' serialiserer korrekt") {
        val hobbits = listOf(
            Hobbit(
                name = Name("Samwise", "Gamgee"),
                age = 38
            ),
            Hobbit(
                name = Name("Frodo", "Baggins"),
                age = 50
            )
        )

        val expectedJson = """
            [
                {
                    "name": {
                        "first": "Samwise",
                        "last": "Gamgee"
                    },
                    "age": 38
                },
                {
                    "name": {
                        "first": "Frodo",
                        "last": "Baggins"
                    },
                    "age": 50
                }
            ]
        """.removeJsonWhitespace()

        val actualJson = hobbits.toJson(Hobbit.serializer()).toString()

        actualJson shouldBe expectedJson
    }

    test("'String.toJson(): JsonElement' serialiserer korrekt") {
        val str = "Meriadoc"

        val expectedJson = "\"Meriadoc\""

        val actualJson = str.toJson().toString()

        actualJson shouldBe expectedJson
    }

    test("'LocalDate.toJson(): JsonElement' serialiserer korrekt") {
        val dato = 16.januar

        val expectedJson = "\"2018-01-16\""

        val actualJson = dato.toJson().toString()

        actualJson shouldBe expectedJson
    }

    test("'UUID.toJson(): JsonElement' serialiserer korrekt") {
        val uuid = MockUuid.uuid

        val expectedJson = "\"${MockUuid.STRING}\""

        val actualJson = uuid.toJson().toString()

        actualJson shouldBe expectedJson
    }

    test("'Map<String, JsonElement>.toJson(): JsonElement' serialiserer korrekt") {
        val tallgrupper = mapOf(
            "partall" to setOf(2, 4, 6),
            "oddetall" to setOf(1, 3, 5, 7)
        )

        val expectedJson = """
            {
                "partall": [2, 4, 6],
                "oddetall": [1, 3, 5, 7]
            }
        """.removeJsonWhitespace()

        val actualJson = tallgrupper.toJson(
            MapSerializer(
                String.serializer(),
                Int.serializer().set()
            )
        ).toString()

        actualJson shouldBe expectedJson
    }

    test("'JsonElement.fromJson(KSerializer<T>): T' deserialiserer korrekt") {
        val merryJson = """
            {
                "name": {
                    "first": "Meriadoc",
                    "last": "Brandybuck"
                },
                "age": 36
            }
        """.removeJsonWhitespace()

        val expectedObject = Hobbit(
            name = Name("Meriadoc", "Brandybuck"),
            age = 36
        )

        val actualObject = merryJson.parseJson().fromJson(Hobbit.serializer())

        actualObject shouldBe expectedObject
    }

    test("'String.fromJson(KSerializer<T>): T' deserialiserer korrekt") {
        val pippinJson = """
            {
                "name": {
                    "first": "Peregrin",
                    "last": "Took"
                },
                "age": 28
            }
        """.removeJsonWhitespace()

        val expectedObject = Hobbit(
            name = Name("Peregrin", "Took"),
            age = 28
        )

        val actualObject = pippinJson.fromJson(Hobbit.serializer())

        actualObject shouldBe expectedObject
    }

    test("'String.parseJson(): JsonElement' serialiserer korrekt") {
        val bilboJson = """
            {
                "name": {
                    "first": "Bilbo",
                    "last": "Baggins"
                },
                "age": 111
            }
        """.removeJsonWhitespace()

        shouldNotThrowAny {
            val parsed = bilboJson.parseJson()

            parsed.jsonObject.let { hobbit ->
                hobbit["name"].shouldNotBeNull().jsonObject.let { name ->
                    name["first"].shouldNotBeNull().jsonPrimitive.content shouldBe "Bilbo"
                    name["last"].shouldNotBeNull().jsonPrimitive.content shouldBe "Baggins"
                }
                hobbit["age"].shouldNotBeNull().jsonPrimitive.content shouldBe "111"
            }
        }
    }

    test("'JsonNode.toJsonElement(): JsonElement' konverterer korrekt") {
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
})

@Serializable
private data class Hobbit(
    val name: Name,
    val age: Int
)

@Serializable
private data class Name(
    val first: String,
    val last: String
)
