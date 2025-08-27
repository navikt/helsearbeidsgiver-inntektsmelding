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
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedForrigeInntekt
import no.nav.helsearbeidsgiver.felles.utils.gjennomsnitt
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.UUID

private val pathMedId = Routes.PREFIX + Routes.HENT_FORESPOERSEL.replaceFirst("{forespoerselId}", UUID.randomUUID().toString())

class HentForespoerselRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `henter forespørsel`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.resultat.tilSuksessJson(),
                )

            val forespoerselId = UUID.randomUUID()
            val response = get(pathMedId.substringBeforeLast("/") + "/$forespoerselId")

            response.status shouldBe HttpStatusCode.OK
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
    fun `henter forespørsel med forslag til forrige inntekt`() =
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

            val response = get(pathMedId)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe resultatMedForrigeInntekt.tilResponseJson()
        }

    @Test
    fun `henter forespørsel med 'null' for verdier som ikke ble hentet`() =
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

            val response = get(pathMedId)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe resultatMedNullVerdier.tilResponseJson()
        }

    @Test
    fun `gir 500-feil hvis Redis timer ut`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows RedisPollerTimeoutException(UUID.randomUUID())

            val response = get(pathMedId)

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `gir 400-feil ved ugyldig forespørsel-ID`() =
        testApi {
            val response = get(pathMedId.substringBeforeLast("/") + "/ugyldig-forespoersel-id")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertNotNull(response.bodyAsText())

            val feilmelding = response.bodyAsText().fromJson(String.serializer())

            assertEquals("Ugyldig parameter: 'ugyldig-forespoersel-id'.", feilmelding)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `gir Forbidden-feil hvis mangler tilgang`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns ikkeTilgangResultat

            val response = get(pathMedId)
            assertEquals(HttpStatusCode.Forbidden, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }

    @Test
    fun `gir Forbidden-feil hvis problemer under henting av tilgang`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns
                ResultJson(
                    failure = "Noe er riv ruskende galt!".toJson(),
                ).toJson()
                    .toString()

            val response = get(pathMedId)
            assertEquals(HttpStatusCode.Forbidden, response.status)

            verify(exactly = 0) {
                mockProducer.send(any<UUID>(), any<Map<Key, JsonElement>>())
            }
        }
}

private object Mock {
    private val orgnr = Orgnr.genererGyldig()

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
            erBegrenset = false,
        )

    val resultat =
        HentForespoerselResultat(
            sykmeldtNavn = "Ola Normann",
            avsenderNavn = "Arbeidsgiver",
            orgNavn = "Norge AS",
            inntekt =
                mapOf(
                    februar(2022) to 2.0,
                    januar(2022) to 1.0,
                    desember(2022) to 3.0,
                ),
            forespoersel = forespoersel,
        )
}

private fun HentForespoerselResultat.tilSuksessJson(): String =
    ResultJson(
        success = toJson(HentForespoerselResultat.serializer()),
    ).toJson()
        .toString()

fun HentForespoerselResultat.tilResponseJson(): String =
    """
    {
        "sykmeldt": {
            "fnr": "${forespoersel.fnr}",
            "navn": ${sykmeldtNavn.jsonStrOrNull()}
        },
        "avsender": {
            "orgnr": "${forespoersel.orgnr}",
            "orgNavn": ${orgNavn.jsonStrOrNull()},
            "navn": ${avsenderNavn.jsonStrOrNull()}
        },
        "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "sykmeldingsperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
        "eksternInntektsdato": ${forespoersel.eksternInntektsdato().jsonStrOrNull()},
        "inntekt": ${inntekt?.hardcodedJson()},
        "forespurtData": ${forespoersel.forespurtData.hardcodedJson()},
        "erBesvart": ${forespoersel.erBesvart},
        "erBegrenset": ${forespoersel.erBegrenset}
    }
    """.removeJsonWhitespace()

private fun Map<YearMonth, Double?>.hardcodedJson(): String =
    """
    {
        "gjennomsnitt": ${gjennomsnitt()},
        "historikk": {${toList().joinToString(transform = Pair<YearMonth, Double?>::hardcodedJson)}}
    }
    """

private fun Pair<YearMonth, Double?>.hardcodedJson(): String = "\"$first\": $second"

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
