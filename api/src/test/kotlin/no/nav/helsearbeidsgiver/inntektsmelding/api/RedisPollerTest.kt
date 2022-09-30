@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Løsning
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class RedisPollerTest {

    private val FNR = "123"
    private val DATA = "Someone"
    private val TOM = ""
    private val UGYLDIG_LISTE = listOf(TOM, TOM, TOM, TOM)
    private val GYLDIG_LISTE = listOf(TOM, TOM, TOM, TOM, DATA)

    @Test
    fun `skal hente ut resultat`() {
        val map = mutableMapOf<String, Løsning>()
        map.put("behov1", Løsning("behov1", "verdi1"))
        map.put("behov2", Løsning("behov2", "verdi2"))
        val data = buildObjectMapper().writeValueAsString(map)
        val resultat = buildPoller(UGYLDIG_LISTE).transformResultat(data, "uuid")
        assertNotNull(resultat)
        assertEquals(2, resultat.løsninger.size)
    }

    @Test
    fun skal_gi_opp_etter_mange_forsøk() {
        runBlocking {
            assertThrows<RedisPollerTimeoutException> {
                buildPoller(UGYLDIG_LISTE).getValue(FNR, 2, 0)
            }
        }
    }

    @Test
    fun skal_ikke_finne_etter_maks_forsøk() {
        runBlocking {
            assertThrows<RedisPollerTimeoutException> {
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
