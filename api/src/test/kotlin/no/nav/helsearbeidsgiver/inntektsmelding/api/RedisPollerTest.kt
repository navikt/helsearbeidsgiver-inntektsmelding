package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

internal class RedisPollerTest {

    private val FNR = "123"
    private val DATA = "Someone"
    private val TOM = ""
    private val UGYLDIG_LISTE = listOf(TOM, TOM, TOM, TOM)
    private val GYLDIG_LISTE = listOf(TOM, TOM, TOM, TOM, DATA)

    @Test
    fun skal_gi_opp_etter_mange_forsøk() {
        runBlocking {
            assertThrows<TimeoutException> {
                val data = buildPoller(UGYLDIG_LISTE).getValue(FNR, 2, 0)
                assertEquals(TOM, data)
            }
        }
    }

    @Test
    fun skal_ikke_finne_etter_maks_forsøk() {
        runBlocking {
            assertThrows<TimeoutException> {
                buildPoller(GYLDIG_LISTE).getValue(FNR, 1, 0)
            }
        }
    }

    @Test
    fun skal_finne_med_tillatt_forsøk() {
        runBlocking {
            val data = buildPoller(GYLDIG_LISTE).getValue(FNR, 5, 0)
            assertEquals(DATA, data)
        }
    }
}
