package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.just
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class FerdigstillForespoerselSakOgOppgaveRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()
        val mockLinkUrl = "mock-url"

        FerdigstillForespoerselSakOgOppgaveRiver(mockLinkUrl, mockAgNotifikasjonKlient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("sak og oppgave ferdigstilles") {
            withData(
                nameFn = { "for event '$it'" },
                listOf(
                    EventName.INNTEKTSMELDING_MOTTATT,
                    EventName.FORESPOERSEL_BESVART,
                ),
            ) { eventName ->
                val innkommendeMelding = innkommendeMelding().copy(eventName = eventName)

                coEvery {
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP, any(), any(), any())
                } just Runs

                coEvery {
                    mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), NotifikasjonTekst.MERKELAPP, any())
                } just Runs

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

                coVerifySequence {
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                        grupperingsid = innkommendeMelding.forespoerselId.toString(),
                        merkelapp = "Inntektsmelding sykepenger",
                        status = SaksStatus.FERDIG,
                        statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                        nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                    )
                    mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                        eksternId = innkommendeMelding.forespoerselId.toString(),
                        merkelapp = "Inntektsmelding sykepenger",
                        nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                    )
                }
            }
        }

        test("sak og oppgave med gammel merkelapp ferdigstilles") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP, any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP_GAMMEL, any(), any(), any())
            } just Runs

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), NotifikasjonTekst.MERKELAPP, any())
            } throws SakEllerOppgaveFinnesIkkeException("We are the knights who say 'Ni!'")

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), NotifikasjonTekst.MERKELAPP_GAMMEL, any())
            } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                )
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.forespoerselId}",
                )
            }
        }

        test("sak ferdigstilles selv om oppgave ikke finnes") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
            } just Runs

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("We are the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding sykepenger", any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding", any())
            }
        }

        test("oppgave ferdigstilles selv om sak ikke finnes") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding", any(), any(), any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding sykepenger", any())
            }
        }

        test("sak og oppgave som ikke finnes håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("We are the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding", any(), any(), any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding sykepenger", any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding", any())
            }
        }

        test("ukjent feil for sak håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
            } throws NullPointerException("It's just a flesh wound")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()
            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding", any(), any(), any())
            }
        }

        test("ukjent feil for oppgave håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
            } just Runs

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws NullPointerException("We are no longer the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding sykepenger", any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), "Inntektsmelding", any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med behov" to Pair(Key.BEHOV, BehovType.LAGRE_SELVBESTEMT_IM.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, forventetFail(innkommendeMelding()).toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any())
                    mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(): FerdigstillForespoerselSakMelding =
    FerdigstillForespoerselSakMelding(
        eventName = EventName.FORESPOERSEL_BESVART,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
    )

private fun forventetUtgaaendeMelding(innkommendeMelding: FerdigstillForespoerselSakMelding): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_FERDIGSTILT.toJson(),
        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
        Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
    )

private fun forventetFail(innkommendeMelding: FerdigstillForespoerselSakMelding): Fail =
    Fail(
        feilmelding = "Klarte ikke ferdigstille sak og/eller oppgave for forespurt inntektmelding.",
        event = innkommendeMelding.eventName,
        transaksjonId = innkommendeMelding.transaksjonId,
        forespoerselId = innkommendeMelding.forespoerselId,
        utloesendeMelding = innkommendeMelding.toMap().toJson(),
    )

private fun FerdigstillForespoerselSakMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
    )
