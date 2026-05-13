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
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.json.ansettelsesforholdSerializer
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentAnsettelsesperioderMelding
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentAnsettelsesperioderRiver
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import no.nav.helsearbeidsgiver.aareg.Ansettelsesforhold as KlientAnsettelsesforhold
import no.nav.helsearbeidsgiver.aareg.Periode as KlientPeriode

class HentAnsettelsesperioderRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAaregClient = mockk<AaregClient>()

        mockConnectToRapid(testRapid) {
            listOf(
                HentAnsettelsesperioderRiver(mockAaregClient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter ansettelsesperioder") {
            coEvery { mockAaregClient.hentAnsettelsesforhold(any(), any()) } returns Mock.ansettelsesforholdFraKlient
            coEvery { mockAaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioderFraKlient

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.ANSETTELSESPERIODER to Mock.ansettelsesperioder.toJson(ansettelsesperioderSerializer))
                            .plus(Key.ANSETTELSESFORHOLD to Mock.ansettelsesforhold.toJson(ansettelsesforholdSerializer))
                            .toJson(),
                )

            coVerifySequence {
                mockAaregClient.hentAnsettelsesforhold(innkommendeMelding.fnr.verdi, innkommendeMelding.kontekstId.toString())
                mockAaregClient.hentAnsettelsesperioder(innkommendeMelding.fnr.verdi, innkommendeMelding.kontekstId.toString())
            }
        }

        test("håndterer feil") {
            coEvery { mockAaregClient.hentAnsettelsesforhold(any(), any()) } returns emptyMap()
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
                mockAaregClient.hentAnsettelsesforhold(innkommendeMelding.fnr.verdi, innkommendeMelding.kontekstId.toString())
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
                    mockAaregClient.hentAnsettelsesforhold(any(), any())
                    mockAaregClient.hentAnsettelsesperioder(any(), any())
                }
            }
        }
    })

private object Mock {
    val orgnr = Orgnr.genererGyldig()

    val periode =
        Periode(
            fom = 1.januar,
            tom = 16.januar,
        )

    val ansettelsesforholdFraKlient =
        mapOf(
            orgnr to
                listOf(
                    KlientAnsettelsesforhold(
                        startdato = periode.fom,
                        sluttdato = periode.tom,
                    ),
                ),
        )

    val ansettelsesforhold =
        mapOf(
            orgnr to
                listOf(
                    Ansettelsesforhold(
                        startdato = periode.fom,
                        sluttdato = periode.tom,
                    ),
                ),
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

    fun innkommendeMelding(): HentAnsettelsesperioderMelding {
        val fnr = Fnr.genererGyldig()
        val svarKafkaKey = KafkaKey(fnr)

        return HentAnsettelsesperioderMelding(
            eventName = EventName.SERVICE_HENT_AKTIVE_ORGNR,
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

    val fail = mockFail("All work and no play makes Jack a dull boy.", EventName.SERVICE_HENT_AKTIVE_ORGNR)
}
