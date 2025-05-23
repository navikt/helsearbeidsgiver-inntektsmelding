package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.KVITTERING

class KvitteringRouteKtTest : ApiTest() {
    @Test
    fun `gir 400-feil ved manglende forespørsel-ID`() =
        testApi {
            val response = get(PATH.substringBeforeLast("/"))
            assertEquals(HttpStatusCode.BadRequest, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `gir 400-feil ved ugyldig forespørsel-ID`() =
        testApi {
            val response = get(PATH.substringBeforeLast("/") + "/ugyldig-forespoersel-id")
            assertEquals(HttpStatusCode.BadRequest, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `skal godta gyldig forespørsel-ID`() =
        testApi {
            val forespoerselId = UUID.randomUUID()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = resultat.toJson(KvitteringResultat.serializer()),
                    ).toJson()
                        .toString(),
                )

            val response = get(PATH.replaceFirst("{forespoerselId}", forespoerselId.toString()))

            assertEquals(HttpStatusCode.OK, response.status)

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
                                    Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
            }
        }
}

private val resultat =
    KvitteringResultat(
        forespoersel = mockForespoersel(),
        sykmeldtNavn = "Syk Meldt",
        orgNavn = "Orga Nisasjon",
        lagret =
            LagretInntektsmelding.Skjema(
                avsenderNavn = "Avs Ender",
                skjema = mockSkjemaInntektsmelding(),
                mottatt = 4.mars.atStartOfDay(),
            ),
    )
