package no.nav.helsearbeidsgiver.felles

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSerializer
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import org.junit.jupiter.api.fail
import kotlin.reflect.KClass

class LøsningTest : FunSpec({
    test("Løsning.Success kan serialiseres og deserialiseres") {
        val successSerialized = Mock.Success.expectedResultat.toLøsningSuccess().let(Json::encodeToJsonElement)

        successSerialized shouldBe Mock.Success.expectedJson

        val successDeserialized = successSerialized.fromJson<Løsning<Samwise>>()

        when (successDeserialized) {
            is Løsning.Success ->
                successDeserialized.resultat shouldBe Mock.Success.expectedResultat
            is Løsning.Failure ->
                successDeserialized::class shouldHaveBeen Løsning.Success::class
        }
    }

    test("Løsning.Failure kan serialiseres og deserialiseres") {
        val failureSerialized = Mock.Failure.expectedFeilmelding.toLøsningFailure().let(Json::encodeToJsonElement)

        failureSerialized shouldBe Mock.Failure.expectedJson

        val failureDeserialized = failureSerialized.fromJson<Løsning<Nothing>>()

        when (failureDeserialized) {
            is Løsning.Success ->
                failureDeserialized::class shouldHaveBeen Løsning.Failure::class
            is Løsning.Failure ->
                failureDeserialized.feilmelding shouldBe Mock.Failure.expectedFeilmelding
        }
    }

    test("Ufullstendig Løsning.Success-json gir DeserializationException") {
        shouldThrowExactly<LøsningSerializer.DeserializationException> {
            """
                {
                   "løsningType": "SUCCESS"
                }
            """
                .removeJsonWhitespace()
                .parseJson()
                .fromJson<Løsning<Samwise>>()
        }
    }

    test("Ufullstendig Løsning.Failure-json gir DeserializationException") {
        shouldThrowExactly<LøsningSerializer.DeserializationException> {
            """
                {
                   "løsningType": "FAILURE"
                }
            """
                .removeJsonWhitespace()
                .parseJson()
                .fromJson<Løsning<Samwise>>()
        }
    }
})

private object Mock {
    object Success {
        val expectedResultat = Samwise(
            nickname = "Samwise the Brave",
            friend = Frodo(
                age = 55,
                pal = Pippin(
                    eats = listOf("Apples", "Mushrooms", "Stolen carrots").map(::Food)
                )
            )
        )

        val expectedJson = """
            {
                "løsningType": "SUCCESS",
                "resultat": {
                    "nickname": "Samwise the Brave",
                    "friend": {
                        "age": 55,
                        "pal": {
                            "eats": [
                                { "food": "Apples" },
                                { "food": "Mushrooms" },
                                { "food": "Stolen carrots" }
                            ]
                        }
                    }
                }
            }
        """
            .removeJsonWhitespace()
            .parseJson()
    }

    object Failure {
        val expectedFeilmelding = "ånei!"

        val expectedJson = """
            {
                "løsningType": "FAILURE",
                "feilmelding":"$expectedFeilmelding"
            }
        """
            .removeJsonWhitespace()
            .parseJson()
    }
}

@Serializable
private data class Samwise(
    val nickname: String,
    val friend: Frodo
)

@Serializable
private data class Frodo(
    val age: Int,
    val pal: Pippin
)

@Serializable
private data class Pippin(
    val eats: List<Food>
)

@Serializable
private data class Food(
    val food: String
)

private infix fun KClass<*>.shouldHaveBeen(expected: KClass<*>): Nothing {
    fail("Forventet klasse '${expected.simpleName}', men fikk klasse '${this.simpleName}'.")
}
