package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.minusData
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InnsendingRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.INNSENDING

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `mottar inntektsmelding og svarer OK`() =
        testApi {
            val skjema = mockSkjemaInntektsmelding()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = skjema.forespoerselId.toJson(),
                    ).toJson()
                        .toString(),
                )

            val response = post(path, skjema, SkjemaInntektsmelding.serializer())

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(InnsendingResponse(skjema.forespoerselId).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())

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
                                            Key.FORESPOERSEL_ID to skjema.forespoerselId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
                mockProducer.send(
                    key = skjema.forespoerselId,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it[Key.DATA]?.toMap()?.shouldContainKey(Key.MOTTATT)
                            it.minus(Key.KONTEKST_ID).minusData(Key.MOTTATT) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.ARBEIDSGIVER_FNR to mockPid.toJson(),
                                            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `mottar delvis inntektsmelding og svarer OK`() =
        testApi {
            val delvisSkjema = mockSkjemaInntektsmelding().copy(agp = null)

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = delvisSkjema.forespoerselId.toJson(),
                    ).toJson()
                        .toString(),
                )

            val response = post(path, delvisSkjema, SkjemaInntektsmelding.serializer())

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(InnsendingResponse(delvisSkjema.forespoerselId).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
        }

    @Test
    fun `gir json-feil ved ugyldig request-json`() =
        testApi {
            val response = post(path, "\"ikke en request\"", String.serializer())

            val feilmelding = response.bodyAsText().fromJson(JsonErrorResponse.serializer())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(null, feilmelding.forespoerselId)
            assertEquals("Feil under serialisering.", feilmelding.error)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() =
        testApi {
            val skjema = mockSkjemaInntektsmelding()

            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows RedisPollerTimeoutException(skjema.forespoerselId)

            val response = post(path, skjema, SkjemaInntektsmelding.serializer())

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(RedisTimeoutResponse(skjema.forespoerselId).toJsonStr(RedisTimeoutResponse.serializer()), response.bodyAsText())
        }
}
