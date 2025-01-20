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
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentArbeidsforholdMelding
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.HentArbeidsforholdRiver
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class HentArbeidsforholdRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAaregClient = mockk<AaregClient>()

        HentArbeidsforholdRiver(mockAaregClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter arbeidsforhold") {
            val expectedArbeidsforhold =
                mockKlientArbeidsforhold()
                    .tilArbeidsforhold()
                    .let(::listOf)

            coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns listOf(mockKlientArbeidsforhold())

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.ARBEIDSFORHOLD to expectedArbeidsforhold.toJson(Arbeidsforhold.serializer()))
                            .toJson(),
                )

            coVerifySequence {
                mockAaregClient.hentArbeidsforhold(innkommendeMelding.fnr.verdi, innkommendeMelding.transaksjonId.toString())
            }
        }

        test("håndterer feil") {
            coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws NullPointerException()

            val innkommendeMelding = Mock.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente arbeidsforhold fra Aareg.",
                    kontekstId = innkommendeMelding.transaksjonId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockAaregClient.hentArbeidsforhold(innkommendeMelding.fnr.verdi, innkommendeMelding.transaksjonId.toString())
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
                    mockAaregClient.hentArbeidsforhold(any(), any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(): HentArbeidsforholdMelding {
        val fnr = Fnr.genererGyldig()
        val svarKafkaKey = KafkaKey(fnr)

        return HentArbeidsforholdMelding(
            eventName = EventName.AKTIVE_ORGNR_REQUESTED,
            behovType = BehovType.HENT_ARBEIDSFORHOLD,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to fnr.toJson(Fnr.serializer()),
                ),
            svarKafkaKey = svarKafkaKey,
            fnr = fnr,
        )
    }

    fun HentArbeidsforholdMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("All work and no play makes Jack a dull boy.", EventName.AKTIVE_ORGNR_REQUESTED)
}
