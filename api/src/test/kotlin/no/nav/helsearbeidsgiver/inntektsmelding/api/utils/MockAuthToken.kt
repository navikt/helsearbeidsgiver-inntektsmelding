package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import no.nav.helsearbeidsgiver.inntektsmelding.api.Auth
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class MockAuthToken {
    private val mockOAuth2Server = MockOAuth2Server()
    private val port = 6666

    val mockPid = Fnr.genererGyldig().verdi

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
