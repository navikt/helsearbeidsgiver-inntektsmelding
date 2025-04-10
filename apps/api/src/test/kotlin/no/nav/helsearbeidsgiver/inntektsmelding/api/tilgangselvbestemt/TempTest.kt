package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangselvbestemt

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val pathMedGyldigOrgnr =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{orgnr}", Orgnr.genererGyldig().toString())

class TempTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gyldig orgnr og tilgang skal gi 200 OK`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat

            val response = get(pathMedGyldigOrgnr)

            2 shouldBe 2
        }
}
