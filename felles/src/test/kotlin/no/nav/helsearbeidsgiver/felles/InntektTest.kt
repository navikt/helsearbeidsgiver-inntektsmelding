package no.nav.helsearbeidsgiver.felles

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
    fun `gjennomsnitt for en maaned er lik som total`() {
        val total = 50000.0
        val inntekt = mockInntekt(
            1 to total
        )

        assertEquals(total, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for to maaneder`() {
        val inntekt = mockInntekt(
            1 to 10.0,
            2 to 20.0
        )
        val forventetSnitt = 15.0

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for tre maaneder når en mangler`() {
        val inntekt = mockInntekt(
            1 to 10.0,
            2 to 20.0,
            3 to null
        )
        val forventetSnitt = 10.0

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for tre maaneder når ingen har verdi`() {
        val inntekt = mockInntekt(
            1 to null,
            2 to null,
            3 to null
        )
        val forventetSnitt = 0.0

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun `test korrekt float ved beregning av gjennomsnitt`() {
        val maanedInntekterPairs = List(11) { (it + 1) to 0.2 }.toTypedArray()
        val inntekt = mockInntekt(*maanedInntekterPairs)

        val total = 2.2
        val forventetSnitt = total / inntekt.maanedOversikt.size

        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }
}

private fun mockInntekt(vararg maanedInntenkterPairs: Pair<Int, Double?>): Inntekt =
    maanedInntenkterPairs.map { (maanedNummer, inntekter) ->
        InntektPerMaaned(
            maaned = YearMonth.of(2018, maanedNummer),
            inntekt = inntekter
        )
    }
        .let(::Inntekt)
