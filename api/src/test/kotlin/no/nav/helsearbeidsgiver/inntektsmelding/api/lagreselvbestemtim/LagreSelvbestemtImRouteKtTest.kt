package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ArbeidsforholdErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
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
    fun `skal godta og returnere id ved gyldig innsending`() =
        testApi {
            mockTilgang(Tilgang.HAR_TILGANG)
            val mockClientId = UUID.randomUUID()
            val selvbestemtId = UUID.randomUUID()
            coEvery { mockRedisPoller.hent(mockClientId) } returns
                ResultJson(
                    success = selvbestemtId.toJson()
                ).toJson(ResultJson.serializer())

            val response =
                mockConstructor(LagreSelvbestemtImProducer::class) {
                    every { anyConstructed<LagreSelvbestemtImProducer>().publish(any(), any()) } returns mockClientId
                    post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(LagreSelvbestemtImResponse(selvbestemtId = selvbestemtId), response.bodyAsText().fromJson(LagreSelvbestemtImResponse.serializer()))
        }

    @Test
    fun `skal returnere bad reqest hvis arbeidsforhold mangler`() =
        testApi {
            mockTilgang(Tilgang.HAR_TILGANG)
            val mockClientId = UUID.randomUUID()
            coEvery { mockRedisPoller.hent(mockClientId) } returns
                ResultJson(
                    failure = "Mangler arbeidsforhold i perioden".toJson()
                ).toJson(ResultJson.serializer())

            val response =
                mockConstructor(LagreSelvbestemtImProducer::class) {
                    every { anyConstructed<LagreSelvbestemtImProducer>().publish(any(), any()) } returns mockClientId
                    post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ArbeidsforholdErrorResponse(), response.bodyAsText().fromJson(ArbeidsforholdErrorResponse.serializer()))
        }
}
