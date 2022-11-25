package no.nav.helsearbeidsgiver.felles.test.mock

import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

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
