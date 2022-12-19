package no.nav.helsearbeidsgiver.felles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

internal class InntektTest {

    @Test
    fun `gjennomsnitt av ingen Inntekt er 0`() {
        val inntekt = Inntekt(emptyList())
        assertEquals(0.0, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for en maaned er lik som total`() {
        val total = 50000.0
        val inntekt = Inntekt(listOf(MottattHistoriskInntekt(YearMonth.now(), total)))
        assertEquals(total, inntekt.gjennomsnitt())
    }

    @Test
    fun `gjennomsnitt for to maaneder`() {
        val inntekter = mapOf(1 to 10.0, 2 to 20.0)
        val inntekt = Inntekt(genererHistoriskInntekt(inntekter))
        val forventetSnitt = 15.0
        assertEquals(forventetSnitt, inntekt.bruttoInntekt)
    }

    @Test
    fun `test korrekt float ved beregning av gjennomsnitt`() {
        val inntekter = List(11) { it to 0.2 }.toMap()
        val total = 2.2
        val inntekt = Inntekt(genererHistoriskInntekt(inntekter))
        val forventetSnitt = total / inntekter.size
        assertEquals(forventetSnitt, inntekt.bruttoInntekt)
    }

    private fun genererHistoriskInntekt(inntekter: Map<Int, Double>): List<MottattHistoriskInntekt> {
        return inntekter.map {
            MottattHistoriskInntekt(null, it.value)
        }
    }
}
