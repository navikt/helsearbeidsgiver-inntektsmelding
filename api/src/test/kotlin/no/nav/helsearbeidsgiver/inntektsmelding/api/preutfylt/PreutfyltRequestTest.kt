@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException

internal class PreutfyltRequestTest {

    @Test
    fun `skal akseptere dersom gyldig orgnummer og fnr`() {
        val request = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
        request.validate()
    }

    @Test
    fun `skal gi feilmelding når orgnummer er ugyldig`() {
        val request = PreutfyltRequest(TestData.notValidOrgNr, TestData.validIdentitetsnummer)
        assertThrows<ConstraintViolationException> {
            request.validate()
        }
    }

    @Test
    fun `skal gi feilmelding når fnr er ugyldig`() {
        val request = PreutfyltRequest(TestData.validOrgNr, TestData.notValidIdentitetsnummer)
        assertThrows<ConstraintViolationException> {
            request.validate()
        }
    }
}
