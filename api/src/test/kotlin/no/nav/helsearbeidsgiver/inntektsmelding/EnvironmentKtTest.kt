package no.nav.helsearbeidsgiver.inntektsmelding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class EnvironmentKtTest {

    @Test
    fun kan_lese_local() {
        assertNotNull(getEnv())
    }
}
