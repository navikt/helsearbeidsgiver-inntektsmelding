package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.client.statement.HttpResponse
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import java.util.UUID
import kotlin.reflect.KClass

object MockUuid {
    const val STRING = "01234567-abcd-0123-abcd-012345678901"
    private val uuid = STRING.let(UUID::fromString)

    fun with(callFn: suspend () -> HttpResponse): HttpResponse =
        mockStatic(UUID::class) {
            every { UUID.randomUUID() } returns uuid

            runBlocking {
                callFn()
            }
        }
}

object MockAuthToken {
    private val server = MockOAuth2Server().apply { start(6666) }

    fun get(): String =
        server.issueToken(
            subject = "mockSubject",
            issuerId = "loginservice-issuer",
            audience = "aud-localhost"
        )
            .serialize()
}

fun <T> mockStatic(klass: KClass<*>, block: () -> T): T {
    mockkStatic(klass)
    return try {
        block()
    } finally {
        unmockkStatic(klass)
    }
}

fun <T> mockConstructor(klass: KClass<*>, block: suspend () -> T): T {
    mockkConstructor(klass)
    return try {
        runBlocking {
            block()
        }
    } finally {
        unmockkConstructor(klass)
    }
}
