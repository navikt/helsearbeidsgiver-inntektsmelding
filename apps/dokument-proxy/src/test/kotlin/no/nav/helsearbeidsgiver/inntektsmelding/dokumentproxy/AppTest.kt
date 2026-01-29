package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.hag.simba.utils.auth.TokenIntrospectionResponse
import no.nav.hag.simba.utils.auth.TokenResponse
import org.junit.jupiter.api.Test
import java.util.UUID

class AppTest {
    private val testUuid = UUID.randomUUID()
    private val mockPdfBytes = "mock-pdf-content".toByteArray()

    @Test
    fun `root endepunkt skal returnere dokument proxy tekst`() =
        testApplication {
            application {
                apiModule(mockk(relaxed = true), mockk(relaxed = true))
            }
            val response = client.get("/")
            response.bodyAsText() shouldBe "dokument proxy"
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `pdf endepunkt krever autentisering`() =
        testWithAuth {
            val response = noRedirectClient.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf")
            response.status shouldBe HttpStatusCode.Found
        }

    @Test
    fun `pdf endepunkt returnerer 200 med gyldig token og vellykket PDF generering`() =
        testWithAuth(mockGyldigToken(), mockPdfClientSuccess()) {
            val response =
                client.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf") {
                    bearerAuth("valid-token")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe String(mockPdfBytes)
        }

    @Test
    fun `pdf endepunkt returnerer 401 når PDF klient returnerer Unauthorized`() =
        testWithAuth(mockGyldigToken(), mockPdfClientUnauthorized()) {
            val response =
                client.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf") {
                    bearerAuth("valid-token")
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `pdf endepunkt returnerer 500 når PDF klient returnerer Failure`() =
        testWithAuth(mockGyldigToken(), mockPdfClientFailure()) {
            val response =
                client.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf") {
                    bearerAuth("valid-token")
                }
            response.status shouldBe HttpStatusCode.InternalServerError
        }

    @Test
    fun `pdf endepunkt returnerer 302 redirect med ugyldig token`() =
        testWithAuth(mockUgyldigToken()) {
            val response =
                noRedirectClient.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf") {
                    bearerAuth("invalid-token")
                }
            response.status shouldBe HttpStatusCode.Found
        }

    @Test
    fun `pdf endepunkt håndterer introspect feil`() =
        testWithAuth(mockIntrospectError()) {
            val response =
                noRedirectClient.get("${Routes.PREFIX}/sykmelding/$testUuid.pdf") {
                    bearerAuth("some-token")
                }
            response.status shouldBe HttpStatusCode.Found
        }

    @Test
    fun `pdf endepunkt returnerer 400 med ugyldig uuid format`() =
        testWithAuth(mockGyldigToken()) {
            val response =
                client.get("${Routes.PREFIX}/sykmelding/ikke-en-uuid.pdf") {
                    bearerAuth("valid-token")
                }
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe "ugyldig sykmelding id"
        }

    private fun testWithAuth(
        authClient: AuthClient = mockk(relaxed = true),
        pdfClient: PdfClient = mockk(relaxed = true),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            apiModule(authClient, pdfClient)
        }
        block()
    }

    private val ApplicationTestBuilder.noRedirectClient: HttpClient
        get() =
            createClient {
                followRedirects = false
            }

    private fun mockGyldigToken(): AuthClient =
        mockk<AuthClient>().apply {
            coEvery { introspect(IdentityProvider.IDPORTEN, any()) } returns
                TokenIntrospectionResponse(active = true)
            coEvery { exchange(IdentityProvider.TOKEN_X, any(), any()) } returns
                TokenResponse(accessToken = "exchanged-token", expiresInSeconds = 3600)
        }

    private fun mockUgyldigToken(): AuthClient =
        mockk<AuthClient>().apply {
            coEvery { introspect(IdentityProvider.IDPORTEN, any()) } returns
                TokenIntrospectionResponse(active = false, error = "token expired")
        }

    private fun mockIntrospectError(): AuthClient =
        mockk<AuthClient>().apply {
            coEvery { introspect(IdentityProvider.IDPORTEN, any()) } throws
                RuntimeException("Network error")
        }

    private fun mockPdfClientSuccess(): PdfClient =
        mockk<PdfClient>().apply {
            coEvery { genererPDF(any(), any()) } returns PdfResponse.Success(mockPdfBytes)
        }

    private fun mockPdfClientUnauthorized(): PdfClient =
        mockk<PdfClient>().apply {
            coEvery { genererPDF(any(), any()) } returns PdfResponse.Unauthorized(HttpStatusCode.Unauthorized)
        }

    private fun mockPdfClientFailure(): PdfClient =
        mockk<PdfClient>().apply {
            coEvery { genererPDF(any(), any()) } returns PdfResponse.Failure(HttpStatusCode.InternalServerError)
        }
}
