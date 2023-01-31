@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RedisPollerTest {
    private val id = "123"
    private val løsningSuccess = "noe data".toLøsningSuccess().let(Json::encodeToJsonElement)
    private val gyldigRedisInnholdListe = List(4) { "" } + løsningSuccess.toString()

    @Test
    fun `skal finne med tillatt antall forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnholdListe)

        val json = runBlocking {
            redisPoller.hent(id, 5, 0)
        }

        json shouldBe løsningSuccess
    }

    @Test
    fun `skal gi opp etter flere forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnholdListe)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(id, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnholdListe)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(id, 1, 0)
            }
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
            val resultat = redisPoller.getResultat(id, 1, 0)
            resultat shouldBe expected
        }
    }
}
