package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.hag.simba.kontrakt.resultat.kvittering.KvitteringResultat
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.forespoersel.test.mockForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.KVITTERING

class KvitteringRouteKtTest : ApiTest() {
    @Test
    fun `skal godta gyldig forespørsel-ID`() =
        testApi {
            val forespoerselId = UUID.randomUUID()

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = resultat.toJson(KvitteringResultat.serializer()),
                    ),
                )

            val response = get(PATH.replaceFirst("{forespoerselId}", forespoerselId.toString()))

            response.status shouldBe HttpStatusCode.OK

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

    @Test
    fun `gir 400-feil ved ugyldig forespørsel-ID`() =
        testApi {
            val response = get(PATH.substringBeforeLast("/") + "/ugyldig-forespoersel-id")

            response.status shouldBe HttpStatusCode.BadRequest

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `gir 404-feil ved manglende forespørsel-ID`() =
        testApi {
            val response = get(PATH.substringBeforeLast("/"))

            response.status shouldBe HttpStatusCode.NotFound

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
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
