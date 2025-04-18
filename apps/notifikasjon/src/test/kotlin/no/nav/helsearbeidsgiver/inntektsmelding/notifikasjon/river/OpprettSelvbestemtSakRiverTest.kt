package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

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
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveDuplikatException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OpprettSelvbestemtSakRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockUrl = "selvbestemt-lenke"
        val mockagNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        OpprettSelvbestemtSakRiver(mockUrl, mockagNotifikasjonKlient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("opprett sak") {
            val sakId = UUID.randomUUID().toString()
            val innkommendeMelding = innkommendeMelding()

            coEvery { mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns sakId

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SELVBESTEMT_INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.SAK_ID to sakId.toJson(),
                        ).toJson(),
                )

            coVerifySequence {
                mockagNotifikasjonKlient.opprettNySak(
                    virksomhetsnummer = innkommendeMelding.inntektsmelding.avsender.orgnr.verdi,
                    merkelapp = "Inntektsmelding sykepenger",
                    grupperingsid =
                        innkommendeMelding.inntektsmelding.type.id
                            .toString(),
                    lenke = "$mockUrl/im-dialog/kvittering/agi/${innkommendeMelding.inntektsmelding.type.id}",
                    tittel =
                        "Inntektsmelding for ${innkommendeMelding.inntektsmelding.sykmeldt.navn}: " +
                            "f. ${innkommendeMelding.inntektsmelding.sykmeldt.fnr.verdi.take(6)}",
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    tilleggsinfo = "Sykmeldingsperiode 05.10.2018 - [...] - 03.11.2018",
                    initiellStatus = SaksStatus.FERDIG,
                    hardDeleteOm = any(),
                )
            }
        }

        test("svarer OK ved duplikat sak") {
            val duplikatSakId = UUID.randomUUID().toString()
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveDuplikatException(duplikatSakId, "mock feilmelding")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SELVBESTEMT_INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.SAK_ID to duplikatSakId.toJson(),
                        ).toJson(),
                )

            coVerifySequence {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("håndterer feil fra klient") {
            val innkommendeMelding = innkommendeMelding()
            val forventetFail = innkommendeMelding.toFail()

            coEvery {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("RIP in peace")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(): OpprettSelvbestemtSakMelding {
    val inntektsmelding = mockInntektsmeldingV1()

    return OpprettSelvbestemtSakMelding(
        eventName = EventName.SELVBESTEMT_IM_MOTTATT,
        behovType = BehovType.OPPRETT_SELVBESTEMT_SAK,
        kontekstId = UUID.randomUUID(),
        data =
            mapOf(
                Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            ),
        inntektsmelding = inntektsmelding,
    )
}

private fun OpprettSelvbestemtSakMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to data.toJson(),
    )

private fun OpprettSelvbestemtSakMelding.toFail(): Fail =
    Fail(
        feilmelding = "Klarte ikke lagre sak for selvbestemt inntektsmelding.",
        kontekstId = kontekstId,
        utloesendeMelding = toMap(),
    )

private val mockFail = mockFail("I know kung-fu.", EventName.SELVBESTEMT_IM_MOTTATT)
