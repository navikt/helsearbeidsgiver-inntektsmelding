package no.nav.helsearbeidsgiver.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel

class ClassUtilsKtTest : FunSpec({

    context("simpleName") {
        test("gir korrekt navn på klasseinstans") {
            val forespoersel = mockForespoersel()

            forespoersel.simpleName() shouldBe "Forespoersel"
        }

        test("gir korrekt navn på primitiv") {
            1.simpleName() shouldBe "Int"
        }

        test("gir korrekt navn innad i klasse") {
            Hobbit().getSimpleName() shouldBe "Hobbit"
        }

        test("gir tom streng for anonymt objekt") {
            val anon = object {}
            anon.simpleName() shouldBe ""
        }
    }
})

private class Hobbit {
    fun getSimpleName(): String =
        simpleName()
}
