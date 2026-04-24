package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private val forespoerselId = UUID.randomUUID()
private val path = Routes.PREFIX + Routes.HENT_ARBEIDSFORHOLD.replaceFirst("{forespoerselId}", forespoerselId.toString())

class HentArbeidsforholdRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gir OK med ansettelsesforhold`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(Mock.ansettelsesforhold),
                )

            val response = get(path)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe Mock.successResponseJson(Mock.ansettelsesforhold)

            verifySequence {
                mockProducer.send(
                    key = mockPid,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.TILGANG_FORESPOERSEL_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FNR to mockPid.toJson(),
                                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
                mockProducer.send(
                    key = forespoerselId,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.AKTIVE_ARBEIDSFORHOLD_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `manglende tilgang gir 403`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns ikkeTilgangResultat

            val response = get(path)

            response.status shouldBe HttpStatusCode.Forbidden

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.failureResult("Noe gikk galt!"),
                )

            val response = get(path)

            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText().fromJson(ErrorResponse.serializer()).shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.emptyResult(),
                )

            val response = get(path)

            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText().fromJson(ErrorResponse.serializer()).shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany listOf(harTilgangResultat, null)

            val response = get(path)

            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText().fromJson(ErrorResponse.serializer()).shouldBeTypeOf<ErrorResponse.RedisTimeout>()
        }

    @Test
    fun `ugyldig UUID i URL gir 400-feil`() =
        testApi {
            val response = get(path.substringBeforeLast("/") + "/ugyldig-forespoersel-id")

            response.status shouldBe HttpStatusCode.BadRequest

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }
}

private object Mock {
    val ansettelsesforhold = listOf(Ansettelsesforhold(startdato = 2.januar, sluttdato = 31.januar))

    fun successResult(ansettelsesforhold: List<Ansettelsesforhold>): ResultJson =
        ResultJson(
            success = ansettelsesforhold.toJson(Ansettelsesforhold.serializer()),
        )

    fun successResponseJson(ansettelsesforhold: List<Ansettelsesforhold>): String =
        """
        {
            "ansettelsesforhold": [${ansettelsesforhold.joinToString(transform = Ansettelsesforhold::hardcodedJson)}]
        }
        """.removeJsonWhitespace()

    fun failureResult(feilmelding: String): ResultJson = ResultJson(failure = feilmelding.toJson())

    fun emptyResult(): ResultJson = ResultJson()
}

private fun Ansettelsesforhold.hardcodedJson(): String {
    val fields = mutableListOf(""""startdato":"$startdato"""")
    if (sluttdato != null) fields.add(""""sluttdato":"$sluttdato"""")
    if (yrkesKode != null) fields.add(""""yrkesKode":"$yrkesKode"""")
    if (yrkesBeskrivelse != null) fields.add(""""yrkesBeskrivelse":"$yrkesBeskrivelse"""")
    if (stillingsprosent != null) fields.add(""""stillingsprosent":$stillingsprosent""")
    return "{${fields.joinToString(",")}}"
}
