package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RedisPollerTest {
    private val key = UUID.randomUUID()
    private val noeData = "noe data".toJson()
    private val gyldigRedisInnhold = noeData.toString()

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal finne med tillatt antall forsøk`() {
        val redisPoller = mockRedisPoller(
            listOf(
                null,
                null,
                gyldigRedisInnhold
            )
        )

        val json = runBlocking {
            redisPoller.hent(key, 3, 0)
        }

        json shouldBe noeData
    }

    @Test
    fun `skal gi opp etter flere forsøk`() {
        val redisPoller = mockRedisPoller(
            listOf(
                null,
                null,
                gyldigRedisInnhold
            )
        )

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(key, 2, 0)
            }
        }
    }

    @Test
    fun `skal ikke finne etter maks forsøk`() {
        val redisPoller = mockRedisPoller(
            listOf(
                null,
                gyldigRedisInnhold
            )
        )

        assertThrows<RedisPollerTimeoutException> {
            runBlocking {
                redisPoller.hent(key, 1, 0)
            }
        }
    }

    @Test
    fun `skal parse forespurt data korrekt`() {
        val expected = mockForespoersel()
        val expectedJson = """
            {
                "type": "${expected.type}",
                "orgnr": "${expected.orgnr}",
                "fnr": "${expected.fnr}",
                "vedtaksperiodeId": ${expected.vedtaksperiodeId.toJson()},
                "sykmeldingsperioder": ${expected.sykmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "egenmeldingsperioder": ${expected.egenmeldingsperioder.toJsonStr(Periode.serializer().list())},
                "bestemmendeFravaersdager": ${expected.bestemmendeFravaersdager.toJsonStr(MapSerializer(String.serializer(), LocalDateSerializer))},
                "forespurtData": ${expected.forespurtData.toJsonStr(ForespurtData.serializer())},
                "erBesvart": ${expected.erBesvart}
            }
        """

        val redisPoller = mockRedisPoller(listOf(expectedJson))

        val resultat = runBlocking {
            redisPoller.hent(key, 5, 0)
        }
            .fromJson(Forespoersel.serializer())

        resultat shouldBe expected
    }
}
