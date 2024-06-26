package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ArbeidsforholdErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreSelvbestemtImRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.SELVBESTEMT_INNTEKTSMELDING

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere id ved gyldig innsending`() = testApi {
        val selvbestemtId = UUID.randomUUID()

        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            ResultJson(
                success = selvbestemtId.toJson()
            ).toJson(ResultJson.serializer())
        )

        val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(LagreSelvbestemtImResponse(selvbestemtId = selvbestemtId), response.bodyAsText().fromJson(LagreSelvbestemtImResponse.serializer()))
    }

    @Test
    fun `skal returnere bad reqest hvis arbeidsforhold mangler`() = testApi {
        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            ResultJson(
                failure = "Mangler arbeidsforhold i perioden".toJson()
            ).toJson(ResultJson.serializer())
        )

        val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ArbeidsforholdErrorResponse(), response.bodyAsText().fromJson(ArbeidsforholdErrorResponse.serializer()))
    }
}
