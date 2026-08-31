package no.nav.helsearbeidsgiver.inntektsmelding.soeknad

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
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
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
import no.nav.helsearbeidsgiver.inntektsmelding.soeknad.Mock.toMap
import no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient.SoeknadKlient
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.september
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentSoeknaderRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockSoeknadKlient = mockk<SoeknadKlient>()

        mockConnectToRapid(testRapid) {
            listOf(
                HentSoeknaderRiver(mockSoeknadKlient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter søknader") {
            val forventedeSoeknader =
                listOf(
                    mockSoeknadArbeidstaker(),
                    mockSoeknadBehandlingsdager(),
                    mockSoeknadArbeidstaker().copy(
                        fom = 14.desember,
                        tom = 25.desember,
                        erGradert = true,
                        egenmeldingerFraSykmelding = emptySet(),
                    ),
                )

            coEvery { mockSoeknadKlient.hentSoeknader(any(), any(), any()) } returns forventedeSoeknader

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.SOEKNAD_LISTE to forventedeSoeknader.toJson(Soeknad.serializer().list()))
                            .toJson(),
                )

            coVerifySequence {
                mockSoeknadKlient.hentSoeknader(
                    orgnr = innkommendeMelding.orgnr.verdi,
                    fnr = innkommendeMelding.fnr.verdi,
                    eldsteFom = innkommendeMelding.eldsteFom,
                )
            }
        }

        test("håndterer feil") {
            coEvery { mockSoeknadKlient.hentSoeknader(any(), any(), any()) } throws NullPointerException()

            val innkommendeMelding = Mock.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente søknader fra Flex.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockSoeknadKlient.hentSoeknader(
                    orgnr = innkommendeMelding.orgnr.verdi,
                    fnr = innkommendeMelding.fnr.verdi,
                    eldsteFom = innkommendeMelding.eldsteFom,
                )
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.HENT_SELVBESTEMT_IM.toJson()),
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
                    mockSoeknadKlient.hentSoeknader(any(), any(), any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(): Melding {
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()
        val eldsteFom = 7.september
        val svarKafkaKey = KafkaKey(fnr)

        return Melding(
            eventName = EventName.SERVICE_HENT_FORESPOERSEL_LISTE,
            behovType = BehovType.HENT_SOEKNAD_LISTE,
            kontekstId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.ORGNR_UNDERENHET to orgnr.toJson(),
                    Key.SYKMELDT_FNR to fnr.toJson(),
                    Key.FRA_OG_MED_DATO to eldsteFom.toJson(),
                ),
            svarKafkaKey = svarKafkaKey,
            orgnr = orgnr,
            fnr = fnr,
            eldsteFom = eldsteFom,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("Pop pop!", EventName.SERVICE_HENT_FORESPOERSEL_LISTE)
}
