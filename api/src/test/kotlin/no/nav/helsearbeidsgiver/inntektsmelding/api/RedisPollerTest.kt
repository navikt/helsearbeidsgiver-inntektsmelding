@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RedisPollerTest {
    private val key = UUID.randomUUID()
    private val noeData = "noe data".toJson()
    private val gyldigRedisInnhold = noeData.toString()

    @Test
    fun `skal finne med tillatt antall forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 4)

        val json = runBlocking {
            redisPoller.hent(key, 5, 0)
        }

        json shouldBe noeData
    }

    @Test
    fun `skal gi opp etter flere forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 5)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(key, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(gyldigRedisInnhold, 5)

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(key, 1, 0)
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
                "vedtaksperiodeId": ${expected.vedtaksperiodeId.toJson()},
                "skjaeringstidspunkt": "${expected.skjaeringstidspunkt}",
                "sykmeldingsperioder": ${expected.sykmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "egenmeldingsperioder": ${expected.egenmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "forespurtData": ${expected.forespurtData.toJsonStr(ForespurtData.serializer())},
                "erBesvart": ${expected.erBesvart}
            }
        """

        val redisPoller = mockRedisPoller(expectedJson, 0)

        val resultat = runBlocking {
            redisPoller.hent(key, 5, 0)
        }
            .fromJson(TrengerInntekt.serializer())

        resultat shouldBe expected
    }
}
