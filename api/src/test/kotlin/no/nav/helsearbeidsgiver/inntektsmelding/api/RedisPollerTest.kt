package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
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
import java.util.UUID

class RedisPollerTest : FunSpec({
    // For å skippe kall til 'delay'
    coroutineTestScope = true

    val key = UUID.randomUUID()
    val dataJson = "noe data".toJson()
    val dataJsonString = dataJson.toString()

    beforeEach {
        clearAllMocks()
    }

    test("skal finne med tillatt antall forsøk") {
        val redisPoller = redisPollerMedMockClient(
            answerOnAttemptNo = 10,
            answer = dataJsonString
        )

        val json = redisPoller.hent(key)

        json shouldBe dataJson
    }

    test("skal ikke finne etter maks forsøk") {
        val redisPoller = redisPollerMedMockClient(
            answerOnAttemptNo = 11,
            answer = dataJsonString
        )

        shouldThrowExactly<RedisPollerTimeoutException> {
            redisPoller.hent(key)
        }
    }

    test("skal parse forespurt data korrekt") {
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

        val redisPoller = redisPollerMedMockClient(
            answerOnAttemptNo = 1,
            answer = expectedJson
        )

        val resultat = redisPoller.hent(key).fromJson(Forespoersel.serializer())

        resultat shouldBe expected
    }
})
