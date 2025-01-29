package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentVirksomhetNavnRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockBrregClient = mockk<BrregClient>()

        HentVirksomhetNavnRiver(mockBrregClient, false).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter navn på alle organisasjoner") {
            val orgnrMedNavn =
                mapOf(
                    Orgnr.genererGyldig() to "Pauls pistasjutsalg",
                    Orgnr.genererGyldig() to "Letos leskedrikker",
                    Orgnr.genererGyldig() to "Jessicas juseri",
                )

            coEvery { mockBrregClient.hentVirksomheter(any()) } returns orgnrMedNavn.map { Virksomhet(organisasjonsnummer = it.key.verdi, navn = it.value) }

            val innkommendeMelding = Mock.innkommendeMelding(orgnrMedNavn.keys)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.VIRKSOMHETER to orgnrMedNavn.mapKeys { it.key.verdi }.toJson())
                            .toJson(),
                )

            coVerifySequence {
                mockBrregClient.hentVirksomheter(orgnrMedNavn.keys.map { it.verdi })
            }
        }

        test("henter navn på noen organisasjoner") {
            val orgnrMedNavn =
                mapOf(
                    Orgnr.genererGyldig() to "Letos leskedrikker",
                    Orgnr.genererGyldig() to "Jessicas juseri",
                )
            val orgnrUtenNavn = Orgnr.genererGyldig()

            coEvery { mockBrregClient.hentVirksomheter(any()) } returns orgnrMedNavn.map { Virksomhet(organisasjonsnummer = it.key.verdi, navn = it.value) }

            val innkommendeMelding = Mock.innkommendeMelding(orgnrMedNavn.keys + orgnrUtenNavn)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.VIRKSOMHETER to orgnrMedNavn.mapKeys { it.key.verdi }.toJson())
                            .toJson(),
                )

            coVerifySequence {
                mockBrregClient.hentVirksomheter(orgnrMedNavn.keys.plus(orgnrUtenNavn).map { it.verdi })
            }
        }

        test("tom liste gir tomt svar") {
            val orgnr =
                setOf(
                    Orgnr.genererGyldig(),
                    Orgnr.genererGyldig(),
                    Orgnr.genererGyldig(),
                )

            coEvery { mockBrregClient.hentVirksomheter(any()) } returns emptyList()

            val innkommendeMelding = Mock.innkommendeMelding(orgnr)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.VIRKSOMHETER to emptyMap<String, String>().toJson())
                            .toJson(),
                )

            coVerifySequence {
                mockBrregClient.hentVirksomheter(orgnr.map { it.verdi })
            }
        }

        test("håndterer feil") {
            coEvery { mockBrregClient.hentVirksomheter(any()) } throws NullPointerException()

            val innkommendeMelding = Mock.innkommendeMelding(setOf(Orgnr.genererGyldig()))

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente virksomhet fra Brreg.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockBrregClient.hentVirksomheter(innkommendeMelding.orgnr.map { it.verdi })
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.TILGANGSKONTROLL.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(setOf(Orgnr.genererGyldig()))
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockBrregClient.hentVirksomheter(any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(orgnr: Set<Orgnr>): HentVirksomhetMelding {
        val svarKafkaKey = KafkaKey(UUID.randomUUID())

        return HentVirksomhetMelding(
            eventName = EventName.TRENGER_REQUESTED,
            behovType = BehovType.HENT_VIRKSOMHET_NAVN,
            kontekstId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.ORGNR_UNDERENHETER to orgnr.toJson(Orgnr.serializer()),
                ),
            svarKafkaKey = svarKafkaKey,
            orgnr = orgnr,
        )
    }

    fun HentVirksomhetMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("failando, failando", EventName.TRENGER_REQUESTED)
}
