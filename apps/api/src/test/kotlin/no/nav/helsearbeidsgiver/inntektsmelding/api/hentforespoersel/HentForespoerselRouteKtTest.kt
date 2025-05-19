package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.domene.ForrigeInntekt
import no.nav.helsearbeidsgiver.felles.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedForrigeInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.jsonStrOrNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.HENT_FORESPOERSEL

class HentForespoerselRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal returnere resultat og status CREATED`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.resultat.tilSuksessJson(),
                )

            val forespoerselId = UUID.randomUUID()
            val response = post(PATH, HentForespoerselRequest(forespoerselId), HentForespoerselRequest.serializer())

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldBe Mock.resultat.tilResponseJson()

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
                                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                            Key.ARBEIDSGIVER_FNR to mockPid.toJson(),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `skal returnere resultat og status CREATED med forespørsel bare inntekt`() =
        testApi {
            val resultatMedForrigeInntekt =
                Mock.resultat
                    .copy(
                        forespoersel =
                            Mock.forespoersel.copy(
                                forespurtData = mockForespurtDataMedForrigeInntekt(),
                            ),
                    )

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    resultatMedForrigeInntekt.tilSuksessJson(),
                )

            val response = post(PATH, HentForespoerselRequest(UUID.randomUUID()), HentForespoerselRequest.serializer())

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldBe resultatMedForrigeInntekt.tilResponseJson()
        }

    @Test
    fun `skal returnere resultat og status CREATED med 'null' for verdier som ikke ble hentet`() =
        testApi {
            val resultatMedNullVerdier =
                Mock.resultat
                    .copy(
                        sykmeldtNavn = null,
                        avsenderNavn = null,
                        orgNavn = null,
                        inntekt = null,
                    )

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    resultatMedNullVerdier.tilSuksessJson(),
                )

            val response = post(PATH, HentForespoerselRequest(UUID.randomUUID()), HentForespoerselRequest.serializer())

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldBe resultatMedNullVerdier.tilResponseJson()
        }

    @Test
    fun `skal returnere Internal server error hvis Redis timer ut`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows RedisPollerTimeoutException(UUID.randomUUID())

            val response = post(PATH, HentForespoerselRequest(UUID.randomUUID()), HentForespoerselRequest.serializer())

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `skal returnere 400-feil ved ugyldig request`() =
        testApi {
            val ugyldigRequest =
                JsonObject(
                    mapOf(
                        HentForespoerselRequest::uuid.name to "ikke en uuid".toJson(),
                    ),
                )

            val response = post(PATH, ugyldigRequest, JsonElement.serializer())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertNotNull(response.bodyAsText())

            val result = response.bodyAsText().fromJson(ResultJson.serializer())

            assertNull(result.success)
            assertNotNull(result.failure)

            val feilmelding = result.failure!!.fromJson(String.serializer())

            assertEquals("Mangler forespørsel-ID for å hente forespørsel.", feilmelding)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `skal returnere Forbidden hvis feil ikke tilgang`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns ikkeTilgangResultat

            val response = post(PATH, HentForespoerselRequest(UUID.randomUUID()), HentForespoerselRequest.serializer())
            assertEquals(HttpStatusCode.Forbidden, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `skal returnere Forbidden hvis feil i Tilgangsresultet`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns
                ResultJson(
                    failure = "Noe er riv ruskende galt!".toJson(),
                ).toJson()
                    .toString()

            val response = post(PATH, HentForespoerselRequest(UUID.randomUUID()), HentForespoerselRequest.serializer())
            assertEquals(HttpStatusCode.Forbidden, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }
}

private object Mock {
    private val orgnr = Orgnr.genererGyldig()

    private val inntekt =
        Inntekt(
            listOf(
                InntektPerMaaned(
                    maaned = februar(2022),
                    inntekt = 2.0,
                ),
                InntektPerMaaned(
                    maaned = januar(2022),
                    inntekt = 1.0,
                ),
                InntektPerMaaned(
                    maaned = desember(2022),
                    inntekt = 3.0,
                ),
            ),
        )

    val forespoersel =
        Forespoersel(
            orgnr = orgnr,
            fnr = Fnr.genererGyldig(),
            vedtaksperiodeId = UUID.randomUUID(),
            sykmeldingsperioder =
                listOf(
                    1.april til 20.april,
                    25.april til 30.april,
                ),
            egenmeldingsperioder =
                listOf(
                    29.mars til 29.mars,
                    31.mars til 31.mars,
                ),
            bestemmendeFravaersdager = mapOf(orgnr to 25.april),
            forespurtData = mockForespurtData(),
            erBesvart = false,
        )

    val resultat =
        HentForespoerselResultat(
            sykmeldtNavn = "Ola Normann",
            avsenderNavn = "Arbeidsgiver",
            orgNavn = "Norge AS",
            inntekt = inntekt,
            forespoersel = forespoersel,
            feil = emptyMap(),
        )
}

private fun HentForespoerselResultat.tilSuksessJson() =
    ResultJson(
        success = toJson(HentForespoerselResultat.serializer()),
    ).toJson()
        .toString()

fun HentForespoerselResultat.tilResponseJson(): String =
    """
    {
        "navn": ${sykmeldtNavn.jsonStrOrNull()},
        "orgNavn": ${orgNavn.jsonStrOrNull()},
        "innsenderNavn": ${avsenderNavn.jsonStrOrNull()},
        "identitetsnummer": "${forespoersel.fnr}",
        "orgnrUnderenhet": "${forespoersel.orgnr}",
        "fravaersperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
        "eksternBestemmendeFravaersdag": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
        "bruttoinntekt": ${inntekt?.gjennomsnitt()},
        "tidligereinntekter": [${inntekt?.maanedOversikt.orEmpty().joinToString(transform = InntektPerMaaned::hardcodedJson)}],
        "forespurtData": ${forespoersel.forespurtData.hardcodedJson()},
        "erBesvart": ${forespoersel.erBesvart}
    }
    """.removeJsonWhitespace()

private fun InntektPerMaaned.hardcodedJson(): String =
    """
    {
        "maaned": "$maaned",
        "inntekt": $inntekt
    }
    """

private fun ForespurtData.hardcodedJson(): String =
    """
    {
        "arbeidsgiverperiode": {
            "paakrevd": ${arbeidsgiverperiode.paakrevd}
        },
        "inntekt": {
            "paakrevd": ${inntekt.paakrevd},
            "forslag": ${inntekt.forslag?.hardcodedJson()}
        },
        "refusjon": {
            "paakrevd": ${refusjon.paakrevd},
            "forslag": ${refusjon.forslag.hardcodedJson()}
        }
    }
    """

private fun ForslagInntekt.hardcodedJson(): String =
    """
    {
        "forrigeInntekt": ${forrigeInntekt?.hardcodedJson()}
    }
    """

private fun ForrigeInntekt.hardcodedJson(): String =
    """
    {
        "skjæringstidspunkt": "$skjæringstidspunkt",
        "kilde": "$kilde",
        "beløp": $beløp
    }
    """

private fun ForslagRefusjon.hardcodedJson(): String =
    """
    {
        "perioder": [${perioder.joinToString(transform = ForslagRefusjon.Periode::hardcodedJson)}],
        "opphoersdato": ${opphoersdato.jsonStrOrNull()}
    }
    """

private fun ForslagRefusjon.Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "beloep": $beloep
    }
    """
