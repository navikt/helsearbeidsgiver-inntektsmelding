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
                // liste til summering, forventet sum
                row(listOf(0.0, 0.0), 0.0),
                row(listOf(1.0, 1.0), 2.0),
                row(listOf(1.0, 2.1), 3.1),
                row(listOf(1.4, 2.5, 3.6), 7.5),
                row(listOf(0.1, 0.2), 0.3), // "vanlig" summering gir avrundingsfeil
                row(listOf(0.6, 4.63), 5.23), // "vanlig" summering gir avrundingsfeil
                row(listOf(1.2, 1.23), 2.43) // "vanlig" summering gir avrundingsfeil
            )
        ) { (addends, expectedSum) ->
            addends.sumMoney() shouldBe expectedSum
        }
    }
})
