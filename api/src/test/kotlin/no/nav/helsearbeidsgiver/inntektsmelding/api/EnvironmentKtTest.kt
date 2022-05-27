package no.nav.helsearbeidsgiver.inntektsmelding.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class EnvironmentKtTest {

    @Test
    @Disabled
    fun kan_lese_local() {
        assertNotNull(getEnv())
    }
}
