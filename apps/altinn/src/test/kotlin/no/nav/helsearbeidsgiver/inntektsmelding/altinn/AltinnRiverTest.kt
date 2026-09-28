package no.nav.helsearbeidsgiver.inntektsmelding.altinn

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.AltinnRiverTest.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class AltinnRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        val mockAltinnClient = mockk<Altinn3M2MClient>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                AltinnRiver(mockAltinnClient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter organisasjonsrettigheter med id fra behov") {
            val innkommendeMelding = Mock.innkommendeMelding()
            val altinnOrgnr = setOf(Orgnr.genererGyldig().verdi)

            coEvery { mockAltinnClient.hentTilganger(any()) } returns altinnOrgnr

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(
                                Key.ORG_RETTIGHETER to altinnOrgnr.toJson(String.serializer().set()),
                            ).toJson(),
                )

            coVerifySequence {
                mockAltinnClient.hentTilganger(innkommendeMelding.fnr.verdi)
            }
        }

        test("håndterer feil") {
            val innkommendeMelding = Mock.innkommendeMelding()

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente organisasjonsrettigheter fra Altinn.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeJsonMap,
                )

            coEvery { mockAltinnClient.hentTilganger(any()) } throws NullPointerException()

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockAltinnClient.hentTilganger(innkommendeMelding.fnr.verdi)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAltinnClient.hentTilganger(any())
                }
            }
        }
    }) {
    private object Mock {
        fun innkommendeMelding(): Melding {
            val fnr = Fnr.genererGyldig()
            val svarKafkaKey = KafkaKey(fnr)

            return Melding(
                eventName = EventName.SERVICE_HENT_AKTIVE_ORGNR,
                behovType = BehovType.ARBEIDSGIVERE,
                kontekstId = UUID.randomUUID(),
                data =
                    mapOf(
                        Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                        Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                    ),
                svarKafkaKey = svarKafkaKey,
                fnr = fnr,
            )
        }

        fun Melding.toMap(): Map<Key, JsonElement> =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to behovType.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to data.toJson(),
            )

        val fail = mockFail("One does not simply walk into Mordor.", EventName.SERVICE_HENT_AKTIVE_ORGNR)
    }
}
