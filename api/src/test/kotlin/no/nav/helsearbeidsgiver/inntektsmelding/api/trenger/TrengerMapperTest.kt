@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.mockArbeidsforhold
import org.junit.jupiter.api.Test
import org.valiktor.ConstraintViolationException
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TrengerMapperTest {

    val løsningNavn = NavnLøsning("abc")
    val løsningVirksomhet = VirksomhetLøsning("xyz")
    val løsningInntekt = InntektLøsning(Inntekt(listOf(MottattHistoriskInntekt(YearMonth.now(), 32_000.0))))
    val løsningArbeidsforhold = buildArbeidsforhold()
    val løsningFeil = NavnLøsning(error = Feilmelding("Feil"))

    fun buildArbeidsforhold(): ArbeidsforholdLøsning =
        mockArbeidsforhold()
            .let(::listOf)
            .let(::ArbeidsforholdLøsning)

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false, false)
        org.junit.jupiter.api.assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(2, constraints.size)
        assertEquals(Key.ORGNRUNDERENHET.str, constraints[0].property)
        assertEquals("Feil", constraints[0].value)
    }

    @Test
    fun `skal returnere feilinformasjon når feil oppstår`() {
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(true, false, false).getStatus())
        assertTrue(buildMapper(true, false, false).hasErrors())
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(false, false, false).getStatus())
        assertTrue(buildMapper(false, false, false).hasErrors())
    }

    @Test
    fun `skal returnere kvittering når det ikke er feil`() {
        assertEquals(HttpStatusCode.Created, buildMapper(true, true, true).getStatus())
        assertFalse(buildMapper(true, true, true).hasErrors())
        buildMapper(true, true, true).getResponse()
    }

    fun buildMapper(en: Boolean, to: Boolean, tre: Boolean): TrengerMapper {
        val feilmelding = Feilmelding("Feil")

        val resultat = Resultat(
            FULLT_NAVN = if (en) løsningNavn else løsningFeil,
            VIRKSOMHET = if (to) løsningVirksomhet else VirksomhetLøsning(error = feilmelding),
            ARBEIDSFORHOLD = løsningArbeidsforhold,
            INNTEKT = if (tre) løsningInntekt else InntektLøsning(error = feilmelding)
        )
        return TrengerMapper("uuid", resultat)
    }
}
