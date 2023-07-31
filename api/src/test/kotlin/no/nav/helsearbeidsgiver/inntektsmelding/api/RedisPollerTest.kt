@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RedisPollerTest {
    private val id = "123"
    private val løsningSuccess = "noe data".toLøsningSuccess().toJson(String.serializer().løsning())
    private val gyldigRedisInnhold = løsningSuccess.toString()

    @Test
    fun `skal finne med tillatt antall forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 4)

        val json = runBlocking {
            redisPoller.hent(id, 5, 0)
        }

        json shouldBe løsningSuccess
    }

    @Test
    fun `skal gi opp etter flere forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 5)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(id, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 5)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(id, 1, 0)
            }
        }
    }

    @Test
    fun `skal parse forespurt data korrekt`() {
        val expected = mockTrengerInntekt()
        val expectedJson = """
            {
                "type": "${expected.type}",
                "orgnr": "${expected.orgnr}",
                "fnr": "${expected.fnr}",
                "skjaeringstidspunkt": "${expected.skjaeringstidspunkt}",
                "sykmeldingsperioder": ${expected.sykmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "egenmeldingsperioder": ${expected.egenmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "forespurtData": ${expected.forespurtData.toJsonStr(ForespurtData.serializer())},
                "erBesvart": ${expected.erBesvart}
            }
        """

        val redisPoller = mockRedisPoller(expectedJson, 0)

        val resultat = runBlocking {
            redisPoller.hent(id, 5, 0)
        }
            .fromJson(TrengerInntekt.serializer())

        resultat shouldBe expected
    }
}
