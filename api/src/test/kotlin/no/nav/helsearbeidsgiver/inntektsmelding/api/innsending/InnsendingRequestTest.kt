@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

internal class InnsendingRequestTest {

    @Test
    fun `skal serialisere InntektEndringÅrsak`() {
        val inntekt = Inntekt(
            bekreftet = false,
            beregnetInntekt = 300.0.toBigDecimal(),
            endringÅrsak = NyStilling(LocalDate.now()),
            manueltKorrigert = false
        )
        println(customObjectMapper().writeValueAsString(inntekt))
    }

    @Test
    fun `skal lese innsendingrequest`() {
        val request: InnsendingRequest = customObjectMapper().readValue("innsendingrequest.json".readResource(), InnsendingRequest::class.java)
        request.validate()
    }

    @Test
    fun `skal kunne konvertere til json`() {
        println(customObjectMapper().writeValueAsString(GYLDIG_INNSENDING_REQUEST))
    }

}
