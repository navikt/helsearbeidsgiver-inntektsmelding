@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class RedisPollerTest {
    private val objectMapper = customObjectMapper()

    private val ID = "123"
    private val DATA = "noe data".toLøsningSuccess().let(objectMapper::writeValueAsString)
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

    @Test
    fun `skal parse liste med forespurt data korrekt`() {
        val expectedTrengerInntekt = mockTrengerInntekt()
        val expected = Resultat(HENT_TRENGER_IM = HentTrengerImLøsning(value = expectedTrengerInntekt))
        val expectedJson = """
            {
                "HENT_TRENGER_IM": {
                    "value": {
                        "orgnr": "${expectedTrengerInntekt.orgnr}",
                        "fnr": "${expectedTrengerInntekt.fnr}",
                        "sykmeldingsperioder": ${expectedTrengerInntekt.sykmeldingsperioder.let(Json::encodeToString)},
                        "forespurtData": ${expectedTrengerInntekt.forespurtData.let(Json::encodeToString)}
                    }
                }
            }
        """

        val redisPoller = mockRedisPoller(listOf(expectedJson))

        runBlocking {
            val data = redisPoller.getResultat(ID, 1, 0)
            assertEquals(expected, data)
        }
    }
}
