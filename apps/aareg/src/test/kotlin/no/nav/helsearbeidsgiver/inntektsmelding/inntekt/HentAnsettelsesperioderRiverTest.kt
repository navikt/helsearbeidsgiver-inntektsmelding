package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

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
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.felles.json.ansettelsesperioderSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentAnsettelsesperioderMelding
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentAnsettelsesperioderRiver
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import no.nav.helsearbeidsgiver.aareg.Periode as KlientPeriode

class HentAnsettelsesperioderRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAaregClient = mockk<AaregClient>()

        HentAnsettelsesperioderRiver(mockAaregClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter ansettelsesperioder") {
            val orgnr = Orgnr.genererGyldig()
            val periode =
                Periode(
                    fom = 1.januar,
                    tom = 16.januar,
                )
            val ansettelsesperioderFraKlient =
                mapOf(
                    orgnr to
                        setOf(
                            KlientPeriode(
                                fom = periode.fom,
                                tom = periode.tom,
                            ),
                        ),
                )
            val ansettelsesperioder =
                mapOf(
                    orgnr to
                        setOf(
                            PeriodeAapen(
                                fom = periode.fom,
                                tom = periode.tom,
                            ),
                        ),
                )

            coEvery { mockAaregClient.hentAnsettelsesperioder(any(), any()) } returns ansettelsesperioderFraKlient

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.ANSETTELSESPERIODER to ansettelsesperioder.toJson(ansettelsesperioderSerializer))
                            .toJson(),
                )

            coVerifySequence {
                mockAaregClient.hentAnsettelsesperioder(innkommendeMelding.fnr.verdi, innkommendeMelding.kontekstId.toString())
            }
        }

        test("håndterer feil") {
            coEvery { mockAaregClient.hentAnsettelsesperioder(any(), any()) } throws NullPointerException()

            val innkommendeMelding = Mock.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente ansettelsesperioder fra Aareg.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockAaregClient.hentAnsettelsesperioder(innkommendeMelding.fnr.verdi, innkommendeMelding.kontekstId.toString())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.HENT_SELVBESTEMT_IM.toJson()),
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
                    mockAaregClient.hentAnsettelsesperioder(any(), any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(): HentAnsettelsesperioderMelding {
        val fnr = Fnr.genererGyldig()
        val svarKafkaKey = KafkaKey(fnr)

        return HentAnsettelsesperioderMelding(
            eventName = EventName.AKTIVE_ORGNR_REQUESTED,
            behovType = BehovType.HENT_ANSETTELSESPERIODER,
            kontekstId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to fnr.toJson(Fnr.serializer()),
                ),
            svarKafkaKey = svarKafkaKey,
            fnr = fnr,
        )
    }

    fun HentAnsettelsesperioderMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("All work and no play makes Jack a dull boy.", EventName.AKTIVE_ORGNR_REQUESTED)
}
