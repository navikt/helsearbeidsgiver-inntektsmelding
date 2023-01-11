@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class RedisPollerTest {
    private val objectMapper = customObjectMapper()

    private val ID = "123"
    private val DATA = Løsning.Success("noe data").let(objectMapper::writeValueAsString)
    private val GYLDIG_LISTE = List(4) { "" } + DATA

    @Test
    fun `skal gi opp etter mange forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(ID, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(ID, 1, 0)
            }
        }
    }

    @Test
    fun `skal finne med tillatt forsøk`() {
        val redisPoller = mockRedisPoller(GYLDIG_LISTE)

        runBlocking {
            val data = redisPoller.hent(ID, 5, 0)

            assertEquals(objectMapper.readTree(DATA), data)
        }
    }
}
