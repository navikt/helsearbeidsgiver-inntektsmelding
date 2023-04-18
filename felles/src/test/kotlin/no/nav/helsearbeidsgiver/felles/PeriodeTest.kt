package no.nav.helsearbeidsgiver.felles

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.Row2
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.test.date.januar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PeriodeTest : FunSpec({

    val p1Start = 1.januar
    val p2End = 10.januar

    val slåSammen = listOf(
        // p1End, p2Start
        row(2.januar, 3.januar),
        row(5.januar, 6.januar),
        row(5.januar, 7.januar),
        row(5.januar, 8.januar),
        row(6.januar, 7.januar),
        row(6.januar, 8.januar),
        row(7.januar, 8.januar)
    )

    val ikkeSlåSammen = listOf(
        // p1End, p2Start
        row(1.januar, 5.januar),
        row(2.januar, 4.januar),
        row(4.januar, 6.januar),
        row(4.januar, 7.januar),
        row(4.januar, 8.januar),
        row(6.januar, 9.januar),
        row(7.januar, 9.januar)
    )

    context("er sammenhengende periode (ignorer helg)") {
        withData(
            nameFn = ::testnavn,
            slåSammen
        ) { (p1End, p2Start) ->
            val p1 = p1Start til p1End
            val p2 = p2Start til p2End

            p1.erSammenhengendeIgnorerHelg(p2).shouldBeTrue()
            p2.erSammenhengendeIgnorerHelg(p1).shouldBeTrue()
        }
    }

    context("er ikke sammenhengende periode (ignorer helg)") {
        withData(
            nameFn = ::testnavn,
            ikkeSlåSammen
        ) { (p1End, p2Start) ->
            val p1 = p1Start til p1End
            val p2 = p2Start til p2End

            p1.erSammenhengendeIgnorerHelg(p2).shouldBeFalse()
            p2.erSammenhengendeIgnorerHelg(p1).shouldBeFalse()
        }
    }

    context("er ikke sammenhengende periode, men overlappende") {
        withData(
            nameFn = ::testnavn,
            listOf(
                // p1End, p2Start
                row(2.januar, 1.januar),
                row(2.januar, 2.januar),
                row(4.januar, 2.januar),
                row(7.januar, 6.januar),
                row(8.januar, 5.januar)
            )
        ) { (p1End, p2Start) ->
            val p1 = p1Start til p1End
            val p2 = p2Start til p2End

            p1.erSammenhengendeIgnorerHelg(p2).shouldBeFalse()
            p2.erSammenhengendeIgnorerHelg(p1).shouldBeFalse()
        }
    }

    context("slå sammen to sammenhengende perioder (ignorer helg)") {
        withData(
            nameFn = ::testnavn,
            slåSammen
        ) { (p1End, p2Start) ->
            val p1 = p1Start til p1End
            val p2 = p2Start til p2End

            val expected = p1.fom til p2.tom

            p1.slåSammenIgnorerHelgOrNull(p2) shouldBe expected
            p2.slåSammenIgnorerHelgOrNull(p1) shouldBe expected
        }
    }

    context("ikke slå sammen to ikke-sammenhengende perioder (ignorer helg)") {
        withData(
            nameFn = ::testnavn,
            ikkeSlåSammen
        ) { (p1End, p2Start) ->
            val p1 = p1Start til p1End
            val p2 = p2Start til p2End

            p1.slåSammenIgnorerHelgOrNull(p2).shouldBeNull()
            p2.slåSammenIgnorerHelgOrNull(p1).shouldBeNull()
        }
    }
})

private fun testnavn(rad: Row2<LocalDate, LocalDate>): String =
    "p1-slutt: ${rad.a.lesbar()}, p2-start: ${rad.b.lesbar()}"

private fun LocalDate.lesbar(): String =
    DateTimeFormatter.ofPattern("E dd.MM")
        .let(this::format)
