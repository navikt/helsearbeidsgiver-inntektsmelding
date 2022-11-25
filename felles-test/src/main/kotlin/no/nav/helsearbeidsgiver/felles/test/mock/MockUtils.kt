package no.nav.helsearbeidsgiver.felles.test.mock

import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

fun <T> mockStatic(klass: KClass<*>, block: suspend () -> T): T {
    mockkStatic(klass)
    return try {
        runBlocking {
            block()
        }
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
