@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
