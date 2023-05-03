@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.test.mock.DOK_MED_NY_INNTEKT
import no.nav.helsearbeidsgiver.felles.test.mock.INNTEKTSMELDING_DOK_MED_GAMMEL_INNTEKT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException
import java.time.LocalDate

internal class InnsendingMapperTest {

    val løsningOk = NavnLøsning(PersonDato("abc", LocalDate.now()))
    val løsningVirksomhet = VirksomhetLøsning("abc")
    val løsningVirksomhetFeil = VirksomhetLøsning(error = Feilmelding("Oops"))
    val løsningFeil = NavnLøsning(error = Feilmelding("Oops"))

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false)
        assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(1, constraints.size)
        assertEquals(Key.ORGNRUNDERENHET.str, constraints[0].property)
        assertEquals("Oops", constraints[0].value)
    }

    @Test
    fun `skal returnere feilinformasjon når feil oppstår`() {
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(true, false).getStatus())
        assertTrue(buildMapper(true, false).hasErrors())
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(false, false).getStatus())
        assertTrue(buildMapper(false, false).hasErrors())
    }

    @Test
    fun `skal returnere kvittering når det ikke er feil`() {
        assertEquals(HttpStatusCode.Created, buildMapper(true, true).getStatus())
        assertFalse(buildMapper(true, true).hasErrors())
    }

    @Test
    fun `skal oversette gammelt inntektbeløp til nytt inntektformat`() {
        // Gammelt format på dokument / payload ligger i databasen for gamle inntektsmeldinger. Disse må vises / konverteres ok.
        val kvitteringResponse = mapInnsending(INNTEKTSMELDING_DOK_MED_GAMMEL_INNTEKT)
        assertEquals(INNTEKTSMELDING_DOK_MED_GAMMEL_INNTEKT.beregnetInntekt, kvitteringResponse.inntekt.beregnetInntekt)
        assertNull(kvitteringResponse.inntekt.endringÅrsak)
        assertFalse(kvitteringResponse.inntekt.manueltKorrigert)
        assertTrue(kvitteringResponse.inntekt.bekreftet)
    }

    @Test
    fun `skal mappe om nytt inntektformat`() {
        // Alle nye dokumenter etter denne endringen vil ha nytt format i db
        val kvitteringResponse = mapInnsending(DOK_MED_NY_INNTEKT)
        assertEquals(DOK_MED_NY_INNTEKT.inntekt, kvitteringResponse.inntekt)
        assertNotNull(kvitteringResponse.inntekt.endringÅrsak)
    }

    fun buildMapper(en: Boolean, to: Boolean): InnsendingMapper {
        val løsninger = mutableListOf<Løsning>()
        løsninger.add(if (en) { løsningOk } else { løsningFeil })
        løsninger.add(if (to) { løsningOk } else { løsningFeil })
        return InnsendingMapper(
            "uuid",
            Resultat(
                FULLT_NAVN = if (en) { løsningOk } else { løsningFeil },
                VIRKSOMHET = if (to) { løsningVirksomhet } else { løsningVirksomhetFeil }
            )
        )
    }
}
