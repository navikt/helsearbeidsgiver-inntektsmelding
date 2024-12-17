package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class FjernLedendeSlashTest {
    @Test
    fun testFjernLedendeSlash() {
        assertEquals("abc", fjernLedendeSlash("/abc"))
        assertEquals("abc", fjernLedendeSlash("abc"))
        val uuid = UUID.randomUUID().toString()
        assertEquals(uuid, fjernLedendeSlash("/$uuid"))
        assertEquals(uuid, fjernLedendeSlash(uuid))
    }
}
