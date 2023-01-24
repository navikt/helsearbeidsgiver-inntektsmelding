package no.nav.helsearbeidsgiver.felles

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løsning.Companion.toLøsning
import no.nav.helsearbeidsgiver.felles.loeser.LøsningDeserializationException
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import org.junit.jupiter.api.fail
import kotlin.reflect.KClass

class LøsningTest : FunSpec({
    test("Løsning.Success kan serialiseres og deserialiseres") {
        val successSerialized = Mock.Success.expectedResultat.toLøsningSuccess().toJson()

        successSerialized shouldBe Mock.Success.expectedJson

        val successDeserialized = successSerialized.toLøsning<Samwise, _>()

        when (successDeserialized) {
            is Løsning.Success ->
                successDeserialized.resultat shouldBe Mock.Success.expectedResultat
            is Løsning.Failure ->
                successDeserialized::class shouldHaveBeen Løsning.Success::class
        }
    }

    test("Løsning.Failure kan serialiseres og deserialiseres") {
        val failureSerialized = Mock.Failure.expectedFeilmelding.toLøsningFailure().toJson()

        failureSerialized shouldBe Mock.Failure.expectedJson

        val failureDeserialized = failureSerialized.toLøsning<String, _>()

        when (failureDeserialized) {
            is Løsning.Success ->
                failureDeserialized::class shouldHaveBeen Løsning.Failure::class
            is Løsning.Failure ->
                failureDeserialized.feilmelding shouldBe Mock.Failure.expectedFeilmelding
        }
    }

    test("Ugyldig Løsning-json gir LøsningDeserializationException") {
        shouldThrowExactly<LøsningDeserializationException> {
            """
                {
                    "neiognei": "lykke til med å lese denne"
                }
            """
                .removeJsonWhitespace()
                .parseJson()
                .toLøsning<Samwise, _>()
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
                "løsningType": "success",
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
                "løsningType": "failure",
                "feilmelding":"$expectedFeilmelding"
            }
        """
            .removeJsonWhitespace()
            .parseJson()
    }
}

private data class Samwise(
    val nickname: String,
    val friend: Frodo
)

private data class Frodo(
    val age: Int,
    val pal: Pippin
)

private data class Pippin(
    val eats: List<Food>
)

private data class Food(
    val food: String
)

private infix fun KClass<*>.shouldHaveBeen(expected: KClass<*>): Nothing {
    fail("Forventet klasse '${expected.simpleName}', men fikk klasse '${this.simpleName}'.")
}
