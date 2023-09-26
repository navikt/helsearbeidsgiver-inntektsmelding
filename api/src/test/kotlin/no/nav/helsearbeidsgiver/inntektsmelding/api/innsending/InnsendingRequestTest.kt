@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InnsendingRequestTest {

    @Test
    fun `skal serialisere InntektEndringÅrsak`() {
        val inntekt = Inntekt(
            bekreftet = false,
            beregnetInntekt = 300.0.toBigDecimal(),
            endringÅrsak = NyStilling(LocalDate.now()),
            manueltKorrigert = false
        )
        println(Jackson.toJson(inntekt))
    }

    @Test
    fun `skal lese innsendingrequest`() {
        val request = "innsendingrequest.json".readResource().let<_, InnsendingRequest>(Jackson::fromJson)
        request.validate()
    }

    @Test
    fun `skal kunne konvertere til json`() {
        println(Jackson.toJson(GYLDIG_INNSENDING_REQUEST))
    }
}
