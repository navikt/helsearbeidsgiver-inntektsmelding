package no.nav.helsearbeidsgiver.felles

import no.nav.helsearbeidsgiver.felles.utils.divideMoney
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

class InntektTest {

    @Test
    fun `gjennomsnitt av ingen Inntekt er 0`() {
        val inntekt = mockInntekt()

        assertEquals(0.0, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt med en maaned er total delt på tre`() {
        val total = 50000.0
        val inntekt = mockInntekt(
            1 to total
        )

        assertEquals(total.divideMoney(3), inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for to maaneder`() {
        val inntekt = mockInntekt(
            1 to 10.0,
            2 to 20.0
        )
        val forventetSnitt = 10.0

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for tre maaneder der en måned ikke har inntekt`() {
        val inntekt = mockInntekt(
            1 to 10.0,
            2 to 0.0,
            3 to 20.0
        )
        val forventetSnitt = 10.0

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun `test korrekt float ved beregning av gjennomsnitt`() {
        val maanedInntekterPairs = List(3) { (it + 1) to 0.2 }.toTypedArray()
        val inntekt = mockInntekt(*maanedInntekterPairs)

        val total = 0.6
        val forventetSnitt = total.divideMoney(3)

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }
}

private fun mockInntekt(vararg maanedInntenkterPairs: Pair<Int, Double>): Inntekt =
    maanedInntenkterPairs.map { (maanedNummer, inntekter) ->
        InntektPerMaaned(
            maaned = YearMonth.of(2018, maanedNummer),
            inntekt = inntekter
        )
    }
        .let(::Inntekt)
