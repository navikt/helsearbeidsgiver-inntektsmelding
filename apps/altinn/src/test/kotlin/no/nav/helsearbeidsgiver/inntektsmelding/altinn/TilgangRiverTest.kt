package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.MockTilgang.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class TilgangRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAltinnClient = mockk<AltinnClient>()

        TilgangRiver(mockAltinnClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("henter tilgang") {
            withData(
                mapOf(
                    "har tilgang" to row(true, Tilgang.HAR_TILGANG),
                    "ikke tilgang" to row(false, Tilgang.IKKE_TILGANG),
                ),
            ) { (altinnSvar, forventetTilgang) ->
                coEvery { mockAltinnClient.harRettighetForOrganisasjon(any(), any()) } returns altinnSvar

                val innkommendeMelding = MockTilgang.innkommendeMelding()

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.DATA to
                            innkommendeMelding.data
                                .plus(Key.TILGANG to forventetTilgang.toJson(Tilgang.serializer()))
                                .toJson(),
                    )

                coVerifySequence {
                    mockAltinnClient.harRettighetForOrganisasjon(innkommendeMelding.fnr.verdi, innkommendeMelding.orgnr.verdi)
                }
            }
        }

        test("håndterer feil") {
            coEvery { mockAltinnClient.harRettighetForOrganisasjon(any(), any()) } throws NullPointerException()

            val innkommendeMelding = MockTilgang.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke sjekke tilgang i Altinn.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockAltinnClient.harRettighetForOrganisasjon(innkommendeMelding.fnr.verdi, innkommendeMelding.orgnr.verdi)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.HENT_SELVBESTEMT_IM.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, MockTilgang.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    MockTilgang
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAltinnClient.harRettighetForOrganisasjon(any(), any())
                }
            }
        }
    })

private object MockTilgang {
    fun innkommendeMelding(): TilgangMelding {
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()

        return TilgangMelding(
            eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED,
            behovType = BehovType.TILGANGSKONTROLL,
            kontekstId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.ORGNR_UNDERENHET to orgnr.toJson(Orgnr.serializer()),
                    Key.FNR to fnr.toJson(Fnr.serializer()),
                ),
            orgnr = orgnr,
            fnr = fnr,
        )
    }

    fun TilgangMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("You shall not pass!", EventName.TILGANG_FORESPOERSEL_REQUESTED)
}
