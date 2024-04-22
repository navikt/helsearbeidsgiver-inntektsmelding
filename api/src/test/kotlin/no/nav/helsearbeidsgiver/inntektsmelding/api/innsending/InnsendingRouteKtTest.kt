@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.test.mock.DELVIS_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.mockDelvisInntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InnsendingRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.INNSENDING + "/${Mock.forespoerselId}"

    private val GYLDIG_REQUEST = GYLDIG_INNSENDING_REQUEST.toJson(Innsending.serializer())
    private val GYLDIG_DELVIS_REQUEST = DELVIS_INNSENDING_REQUEST.toJson(Innsending.serializer())

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere kvittering`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        val mockClientId = UUID.randomUUID()

        coEvery {
            anyConstructed<RedisPoller>().hent(mockClientId, any(), any())
        } returns mockInntektsmelding().toJson(Inntektsmelding.serializer())

        val response = mockConstructor(InnsendingProducer::class) {
            every {
                anyConstructed<InnsendingProducer>().publish(any(), any(), any())
            } returns mockClientId

            post(path, GYLDIG_REQUEST)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(InnsendingResponse(Mock.forespoerselId).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `gir json-feil ved ugyldig request-json`() = testApi {
        val response = post(path, "\"ikke en request\"".toJson())

        val feilmelding = response.bodyAsText().fromJson(JsonErrorResponse.serializer())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(Mock.forespoerselId.toString(), feilmelding.forespoerselId)
        assertEquals("Feil under serialisering.", feilmelding.error)
    }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        val mockClientId = UUID.randomUUID()

        coEvery {
            anyConstructed<RedisPoller>().hent(mockClientId, any(), any())
        } throws RedisPollerTimeoutException(Mock.forespoerselId)

        val response = mockConstructor(InnsendingProducer::class) {
            every {
                anyConstructed<InnsendingProducer>().publish(any(), any(), any())
            } returns mockClientId

            post(path, GYLDIG_REQUEST)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(RedisTimeoutResponse(Mock.forespoerselId).toJsonStr(RedisTimeoutResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `skal godta delvis im og returnere kvittering`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        val mockClientId = UUID.randomUUID()

        coEvery {
            anyConstructed<RedisPoller>().hent(mockClientId, any(), any())
        } returns mockDelvisInntektsmeldingDokument().toJson(Inntektsmelding.serializer())

        val response = mockConstructor(InnsendingProducer::class) {
            every {
                anyConstructed<InnsendingProducer>().publish(any(), any(), any())
            } returns mockClientId

            post(path, GYLDIG_DELVIS_REQUEST)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(InnsendingResponse(Mock.forespoerselId).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
    }
}
