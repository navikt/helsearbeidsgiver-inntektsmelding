package no.nav.hag.simba.utils.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.test.date.mai
import java.time.Month
import java.time.YearMonth

class DateUtilsKtTest :
    FunSpec({

        test("'LocalDate.toYearMonth(): YearMonth' gir måneden for datoen") {
            val date = 1.mai(2015)

            val actual = date.toYearMonth()

            actual shouldBe YearMonth.of(2015, Month.MAY)
        }

        test("'YearMonth.toLocalDate(day: Int): LocalDate' gir datoen for måned med spesifisert dag") {
            val month = YearMonth.of(2015, Month.MAY)

            val actual = month.toLocalDate(15)

            actual shouldBe 15.mai(2015)
        }
    })
