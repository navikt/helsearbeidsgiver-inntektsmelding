package no.nav.helsearbeidsgiver.felles.test.mock

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class RandomDigitStringKtTest : FunSpec({

    context(::randomDigitString.name) {
        test("streng inneholder bare tall") {
            randomDigitString(54) shouldMatch Regex("\\d+")
        }

        test("streng er korrekt lengde") {
            randomDigitString(22) shouldHaveLength 22
        }

        test("negativ input kaster exception") {
            shouldThrowExactly<IllegalArgumentException> {
                randomDigitString(-4)
            }
        }
    }
})
