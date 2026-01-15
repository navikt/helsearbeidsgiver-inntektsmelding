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
import org.junit.jupiter.api.Test

class AppTest {
    @Test
    fun `root endpoint skal returnere dokument proxy tekst`() =
        testApplication {
            application {
                apiModule(mockk(relaxed = true))
            }
            val response = client.get("/")
            response.bodyAsText() shouldBe "dokument proxy"
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `pdf endpoint krever autentisering`() =
        testWithAuth {
            val response = noRedirectClient.get("${Routes.PREFIX}/pdf")
            response.status shouldBe HttpStatusCode.Found
        }

    @Test
    fun `pdf endpoint returnerer 200 med gyldig token`() =
        testWithAuth(mockGyldigToken()) {
            val response =
                client.get("${Routes.PREFIX}/pdf") {
                    bearerAuth("valid-token")
                }
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `pdf endpoint returnerer 302 redirect med ugyldig token`() =
        testWithAuth(mockUgyldigToken()) {
            val response =
                noRedirectClient.get("${Routes.PREFIX}/pdf") {
                    bearerAuth("invalid-token")
                }
            response.status shouldBe HttpStatusCode.Found
        }

    @Test
    fun `pdf endpoint håndterer introspect feil`() =
        testWithAuth(mockIntrospectError()) {
            val response =
                noRedirectClient.get("${Routes.PREFIX}/pdf") {
                    bearerAuth("some-token")
                }
            response.status shouldBe HttpStatusCode.Found
        }

    private fun testWithAuth(
        authClient: AuthClient = mockk(relaxed = true),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            apiModule(authClient)
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
}
