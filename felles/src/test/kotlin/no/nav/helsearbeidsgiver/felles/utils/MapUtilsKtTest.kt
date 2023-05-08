package no.nav.helsearbeidsgiver.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MapUtilsKtTest : FunSpec({

    test("mapKeysNotNull transforms keys and removes transformations returning null") {
        val numberStrsByNumbers = mapOf(
            1 to "one",
            2 to "two",
            3 to "three",
            4 to "four"
        )

        val evenNumberStrsByNumbers = numberStrsByNumbers.mapKeysNotNull {
            if (it % 2 == 0) {
                it
            } else {
                null
            }
        }

        evenNumberStrsByNumbers shouldBe mapOf(
            2 to "two",
            4 to "four"
        )
    }

    test("mapValuesNotNull transforms values and removes transformations returning null") {
        val numbersByNumberStrs = mapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4
        )

        val oddNumbersByNumberStrs = numbersByNumberStrs.mapValuesNotNull {
            if (it % 2 == 1) {
                it
            } else {
                null
            }
        }

        oddNumbersByNumberStrs shouldBe mapOf(
            "one" to 1,
            "three" to 3
        )
    }
})
