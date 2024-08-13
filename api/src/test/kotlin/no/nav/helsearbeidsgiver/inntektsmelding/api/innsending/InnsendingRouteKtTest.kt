package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockDelvisInntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
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
    private val path = Routes.PREFIX + Routes.INNSENDING + "/${UUID.randomUUID()}"

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
                        success = mockInntektsmelding().toJson(Inntektsmelding.serializer()),
                    ).toJson(ResultJson.serializer())
                        .toString(),
                )

            val response = post(path, skjema, SkjemaInntektsmelding.serializer())

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(InnsendingResponse(skjema.forespoerselId).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
        }

    @Test
    fun `mottar delvis inntektsmelding og svarer OK`() =
        testApi {
            val delvisSkjema = mockSkjemaInntektsmelding().copy(agp = null)

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = mockDelvisInntektsmeldingDokument().toJson(Inntektsmelding.serializer()),
                    ).toJson(ResultJson.serializer())
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
