@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSuccess
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class RedisPollerTest {
    private val objectMapper = customObjectMapper()

    private val ID = "123"
    private val DATA = LøsningSuccess("noe data")
    private val GYLDIG_LISTE = List(4) { "" } + DATA.let(objectMapper::writeValueAsString)

    @Test
    fun `skal gi opp etter mange forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent<String>(ID, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent<String>(ID, 1, 0)
            }
        }
    }

    @Test
    fun `skal finne med tillatt forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        runBlocking {
            val data = redisPoller.hent<String>(ID, 5, 0)

            assertEquals(DATA, data)
        }
    }
}
