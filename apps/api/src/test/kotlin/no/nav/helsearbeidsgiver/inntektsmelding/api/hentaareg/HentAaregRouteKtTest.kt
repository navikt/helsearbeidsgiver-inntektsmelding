package no.nav.helsearbeidsgiver.inntektsmelding.api.hentaareg

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
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
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
private val path = Routes.PREFIX + Routes.HENT_AAREG.replaceFirst("{forespoerselId}", forespoerselId.toString())

class HentAaregRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gir OK med ansettelsesperioder`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(Mock.ansettelsesperioder),
                )

            val response = get(path)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe Mock.successResponseJson(Mock.ansettelsesperioder)

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
    val ansettelsesperioder = listOf(PeriodeAapen(fom = 2.januar, tom = 31.januar))

    fun successResult(ansettelsesperioder: List<PeriodeAapen>): ResultJson =
        ResultJson(
            success = ansettelsesperioder.toJson(PeriodeAapen.serializer()),
        )

    fun successResponseJson(ansettelsesperioder: List<PeriodeAapen>): String =
        """
        {
            "ansettelsesperioder": [${ansettelsesperioder.joinToString(transform = PeriodeAapen::hardcodedJson)}]
        }
        """.removeJsonWhitespace()

    fun failureResult(feilmelding: String): ResultJson = ResultJson(failure = feilmelding.toJson())

    fun emptyResult(): ResultJson = ResultJson()
}

private fun PeriodeAapen.hardcodedJson(): String = """{"fom":"$fom","tom":${if (tom != null) "\"$tom\"" else "null"}}"""
