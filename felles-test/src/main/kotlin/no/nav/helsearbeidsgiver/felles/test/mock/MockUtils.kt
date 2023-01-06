package no.nav.helsearbeidsgiver.felles.test.mock

import io.ktor.client.statement.HttpResponse
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.reflect.KClass

object MockUuid {
    const val STRING = "01234567-abcd-0123-abcd-012345678901"
    val uuid: UUID = STRING.let(UUID::fromString)

    fun with(callFn: suspend () -> HttpResponse): HttpResponse =
        mockStatic(UUID::class) {
            every { UUID.randomUUID() } returns uuid

            callFn()
        }
}

fun <T> mockStatic(klass: KClass<*>, block: suspend () -> T): T =
    runWithSetup(
        block = block,
        setup = { mockkStatic(klass) },
        teardown = { unmockkStatic(klass) }
    )

fun <T> mockConstructor(klass: KClass<*>, block: suspend () -> T): T =
    runWithSetup(
        block = block,
        setup = { mockkConstructor(klass) },
        teardown = { unmockkConstructor(klass) }
    )

fun <T> mockObject(obj: Any, block: suspend () -> T): T =
    runWithSetup(
        block = block,
        setup = { mockkObject(obj) },
        teardown = { unmockkObject(obj) }
    )

private fun <T> runWithSetup(
    block: suspend () -> T,
    setup: () -> Unit,
    teardown: () -> Unit
): T {
    setup()
    return try {
        runBlocking {
            block()
        }
    } finally {
        teardown()
    }
}
