package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.client.statement.HttpResponse
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.test.mock.mockStatic
import no.nav.helsearbeidsgiver.inntektsmelding.api.Auth
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

abstract class MockAuthToken {
    private val mockOAuth2Server = MockOAuth2Server()
    private val port = 6666

    val mockPid = "12345678901"

    fun mockAuthToken(): String =
        mockOAuth2Server.issueToken(
            issuerId = Auth.ISSUER,
            subject = "mockSubject",
            audience = "aud-localhost",
            claims = mapOf(
                Auth.CLAIM_PID to mockPid
            )
        )
            .serialize()

    @BeforeEach
    fun start() {
        mockOAuth2Server.start(port)
    }

    @AfterEach
    fun stop() {
        mockOAuth2Server.shutdown()
    }
}

object MockUuid {
    const val STRING = "01234567-abcd-0123-abcd-012345678901"
    private val uuid = STRING.let(UUID::fromString)

    fun with(callFn: suspend () -> HttpResponse): HttpResponse =
        mockStatic(UUID::class) {
            every { UUID.randomUUID() } returns uuid

            callFn()
        }
}
