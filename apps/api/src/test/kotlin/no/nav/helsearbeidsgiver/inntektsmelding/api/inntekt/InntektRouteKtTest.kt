package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InntektRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.INNTEKT

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `henter inntekt`() =
        testApi {
            val request =
                InntektRequest(
                    forespoerselId = UUID.randomUUID(),
                    inntektsdato = 17.april(2024),
                )
            val inntekt =
                mapOf(
                    mars(2024) to 33330.0,
                    februar(2024) to 22220.0,
                    januar(2024) to 11110.0,
                )
            val forventetResponse =
                InntektResponse(
                    gjennomsnitt = inntekt.gjennomsnitt(),
                    historikk = inntekt,
                )

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = inntekt.toJson(inntektMapSerializer),
                    ).toJson()
                        .toString(),
                )

            val response = post(path, request, InntektRequest.serializer())

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe forventetResponse.toJsonStr(InntektResponse.serializer())

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
                                            Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
                mockProducer.send(
                    key = request.forespoerselId,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                                            Key.INNTEKTSDATO to request.inntektsdato.toJson(),
                                        ).toJson(),
                                )
                        },
                )
            }
        }
}
