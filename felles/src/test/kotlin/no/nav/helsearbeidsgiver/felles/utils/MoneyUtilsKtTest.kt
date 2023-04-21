package no.nav.helsearbeidsgiver.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class MoneyUtilsKtTest : FunSpec({
    context(List<Double>::sumMoney.name) {
        withData(
            nameFn = { (addends, expectedSum) ->
                "${addends.joinToString(separator = " + ")} = $expectedSum"
            },
            listOf(
                row(/*sum of*/ listOf(0.0, 0.0), /*should be*/ 0.0),
                row(/*sum of*/ listOf(1.0, 1.0), /*should be*/ 2.0),
                row(/*sum of*/ listOf(1.0, 2.1), /*should be*/ 3.1),
                row(/*sum of*/ listOf(1.4, 2.5, 3.6), /*should be*/ 7.5),
                row(/*sum of*/ listOf(0.1, 0.2), /*should be*/ 0.3), // "vanlig" summering gir avrundingsfeil
                row(/*sum of*/ listOf(0.6, 4.63), /*should be*/ 5.23), // "vanlig" summering gir avrundingsfeil
                row(/*sum of*/ listOf(1.2, 1.23), /*should be*/ 2.43) // "vanlig" summering gir avrundingsfeil
            )
        ) { (addends, expectedSum) ->
            addends.sumMoney() shouldBe expectedSum
        }
    }

    context(Double::divideMoney.name) {
        withData(
            nameFn = { (dividend, divisor, expectedResult) ->
                "$dividend / $divisor = $expectedResult"
            },
            listOf(
                row(0.01, /*divided by*/ 2, /*should be*/ 0.01),
                row(0.0099, /*divided by*/ 2, /*should be*/ 0.0),
                row(0.1, /*divided by*/ 2, /*should be*/ 0.05),
                row(0.2, /*divided by*/ 2, /*should be*/ 0.1),
                row(1.0, /*divided by*/ 3, /*should be*/ 0.33),
                row(170050.9595, /*divided by*/ 98, /*should be*/ 1735.21),
                row(400834.22689, /*divided by*/ 49, /*should be*/ 8180.29),
                row(154285.589641, /*divided by*/ 81, /*should be*/ 1904.76),
                row(400828.343788, /*divided by*/ 20, /*should be*/ 20041.42),
                row(647616.8195438, /*divided by*/ 76, /*should be*/ 8521.27),
                row(446188.9729658, /*divided by*/ 16, /*should be*/ 27886.81),
                row(87386.821519898, /*divided by*/ 67, /*should be*/ 1304.28),
                row(909129.17961885, /*divided by*/ 31, /*should be*/ 29326.75),
                row(657053.2471867118, /*divided by*/ 85, /*should be*/ 7730.04),
                row(810280.6871008531, /*divided by*/ 42, /*should be*/ 19292.4)
            )
        ) { (dividend, divisor, expectedResult) ->
            dividend.divideMoney(divisor) shouldBe expectedResult
        }
    }
})
