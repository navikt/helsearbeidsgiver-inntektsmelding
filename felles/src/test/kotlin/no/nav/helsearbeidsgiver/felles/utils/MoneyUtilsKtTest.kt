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
                // Sum of A should be B
                row(listOf(0.0, 0.0), 0.0),
                row(listOf(1.0, 1.0), 2.0),
                row(listOf(1.0, 2.1), 3.1),
                row(listOf(1.4, 2.5, 3.6), 7.5),
                row(listOf(0.1, 0.2), 0.3), // "vanlig" summering gir avrundingsfeil
                row(listOf(0.6, 4.63), 5.23), // "vanlig" summering gir avrundingsfeil
                row(listOf(1.2, 1.23), 2.43), // "vanlig" summering gir avrundingsfeil
            ),
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
                // A divided by B should be C
                row(0.01, 2, 0.01),
                row(0.0099, 2, 0.0),
                row(0.1, 2, 0.05),
                row(0.2, 2, 0.1),
                row(1.0, 3, 0.33),
                row(170050.9595, 98, 1735.21),
                row(400834.22689, 49, 8180.29),
                row(154285.589641, 81, 1904.76),
                row(400828.343788, 20, 20041.42),
                row(647616.8195438, 76, 8521.27),
                row(446188.9729658, 16, 27886.81),
                row(87386.821519898, 67, 1304.28),
                row(909129.17961885, 31, 29326.75),
                row(657053.2471867118, 85, 7730.04),
                row(810280.6871008531, 42, 19292.4),
            ),
        ) { (dividend, divisor, expectedResult) ->
            dividend.divideMoney(divisor) shouldBe expectedResult
        }
    }
})
