package no.nav.hag.simba.utils.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import java.time.YearMonth

class MoneyUtilsKtTest :
    FunSpec({
        context(Map<YearMonth, Double?>::gjennomsnitt.name) {
            withData(
                nameFn = { (inntekt, expectedAverage) ->
                    "(${inntekt.values.joinToString(separator = " + ")}) / ${inntekt.size} = $expectedAverage"
                },
                listOf<Pair<Map<YearMonth, Double?>, Double>>(
                    // Average of A.values should be B
                    Pair(emptyMap(), 0.0),
                    Pair(mapOf(juni(2024) to null), 0.0),
                    Pair(mapOf(juni(2024) to 0.0), 0.0),
                    Pair(mapOf(juni(2024) to 97.0), 97.0),
                    Pair(mapOf(juni(2024) to 55.0, juli(2024) to null), 27.5),
                    Pair(mapOf(juni(2024) to 366.0, juli(2024) to 0.0), 183.0),
                    Pair(mapOf(juni(2024) to 717.0, juli(2024) to 33.0), 375.0),
                    Pair(mapOf(juni(2024) to 840.0, juli(2024) to 38.52, august(2024) to 80.155), 319.56),
                    Pair(mapOf(juni(2024) to 505.23, juli(2024) to 114.333, august(2024) to 7.696), 209.09),
                    Pair(mapOf(juni(2024) to 68.44, juli(2024) to 4298.59, august(2024) to 9000.99), 4456.01),
                ),
            ) { (inntekt, expectedAverage) ->
                inntekt.gjennomsnitt() shouldBe expectedAverage
            }
        }

        context(List<Double>::sumMoney.name) {
            withData(
                nameFn = { (addends, expectedSum) ->
                    "${addends.joinToString(separator = " + ")} = $expectedSum"
                },
                listOf(
                    // Sum of A should be B
                    listOf(0.0, 0.0) to 0.0,
                    listOf(1.0, 1.0) to 2.0,
                    listOf(1.0, 2.1) to 3.1,
                    listOf(1.4, 2.5, 3.6) to 7.5,
                    listOf(0.1, 0.2) to 0.3, // "vanlig" summering gir avrundingsfeil
                    listOf(0.6, 4.63) to 5.23, // "vanlig" summering gir avrundingsfeil
                    listOf(1.2, 1.23) to 2.43, // "vanlig" summering gir avrundingsfeil
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
                    Triple(0.01, 2, 0.01),
                    Triple(0.0099, 2, 0.0),
                    Triple(0.1, 2, 0.05),
                    Triple(0.2, 2, 0.1),
                    Triple(1.0, 3, 0.33),
                    Triple(170050.9595, 98, 1735.21),
                    Triple(400834.22689, 49, 8180.29),
                    Triple(154285.589641, 81, 1904.76),
                    Triple(400828.343788, 20, 20041.42),
                    Triple(647616.8195438, 76, 8521.27),
                    Triple(446188.9729658, 16, 27886.81),
                    Triple(87386.821519898, 67, 1304.28),
                    Triple(909129.17961885, 31, 29326.75),
                    Triple(657053.2471867118, 85, 7730.04),
                    Triple(810280.6871008531, 42, 19292.4),
                ),
            ) { (dividend, divisor, expectedResult) ->
                dividend.divideMoney(divisor) shouldBe expectedResult
            }
        }
    })
