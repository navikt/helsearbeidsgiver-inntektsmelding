package no.nav.helsearbeidsgiver.felles

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.YearMonth

internal class InntektTest {

    @Test
    fun gjennomsnitt_Ingen_Inntekt_er_0() {
        val inntekt = Inntekt(0.0, emptyList())
        assertEquals(0.0, inntekt.gjennomsnitt())
    }

    @Test
    fun gjennomsnitt_for_en_eller_ingen_maaned_er_lik_som_total() {
        val total = 50000.0
        val inntekt = Inntekt(total, emptyList()) // spesialtilfelle, liste bør aldri være tom, men håndter det likevel
        assertEquals(total, inntekt.gjennomsnitt())
        val inntekt2 = Inntekt(total, listOf(MottattHistoriskInntekt(YearMonth.now(), total)))
        assertEquals(total, inntekt2.gjennomsnitt())
    }

    @Test
    fun gjennomsnitt_for_to_maaneder() {
        val inntekter = mapOf(1 to 10.0, 2 to 20.0)
        val total = inntekter.values.sum()
        val inntekt = Inntekt(total, genererHistoriskInntekt(inntekter))
        val forventetSnitt = total / inntekter.size
        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    @Test
    fun test_float_feil() {
        val inntekter = mutableMapOf<Int, Double>()
        for (x in 1..11) {
            inntekter.put(x, 0.2)
        }
        //val total = inntekter.values.sum() Funker ikke..!
        val total = 2.2
        val inntekt = Inntekt(total, genererHistoriskInntekt(inntekter))
        val forventetSnitt = total / inntekter.size
        assertEquals(forventetSnitt, inntekt.gjennomsnitt())
    }

    private fun genererHistoriskInntekt(inntekter: Map<Int, Double>): List<MottattHistoriskInntekt> {
        val historiskInntekt = mutableListOf<MottattHistoriskInntekt>()
        if (inntekter.isEmpty()) return historiskInntekt
        for ((_, inntekt) in inntekter) {
            historiskInntekt.add(MottattHistoriskInntekt(null, inntekt))
        }
        return historiskInntekt
    }
}
