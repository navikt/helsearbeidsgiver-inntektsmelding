package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.fail
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
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.felles.utils.erForespurt
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

class FerdigstillSakOgOppgaveRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()
        val mockLinkUrl = "mock-url"

        mockConnectToRapid(testRapid) {
            listOf(
                FerdigstillSakOgOppgaveRiver(mockLinkUrl, mockAgNotifikasjonKlient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("sak og oppgave ferdigstilles") {
            withData(
                nameFn = { "for event '$it'" },
                listOf(
                    EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
                    EventName.SELVBESTEMT_IM_LAGRET,
                    EventName.FORESPOERSEL_BESVART,
                ),
            ) { eventName ->
                val innkommendeMelding = innkommendeMelding(eventName)

                coEvery {
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                } just Runs

                coEvery {
                    mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
                } just Runs

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

                val erForespurt = innkommendeMelding.imType.erForespurt()

                coVerifySequence {
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                        grupperingsid = innkommendeMelding.imType.id.toString(),
                        merkelapp = "Inntektsmelding sykepenger",
                        status = SaksStatus.FERDIG,
                        statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                        nyLenke =
                            if (erForespurt) {
                                "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.imType.id}"
                            } else {
                                null
                            },
                        hardDeleteOm = sakLevetid,
                    )
                    if (erForespurt) {
                        mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                            eksternId = innkommendeMelding.imType.id.toString(),
                            merkelapp = "Inntektsmelding sykepenger",
                            nyLenke = "$mockLinkUrl/im-dialog/kvittering/${innkommendeMelding.imType.id}",
                        )
                    }
                }
            }
        }

        test("sak ferdigstilles selv om oppgave ikke finnes") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            } just Runs

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("We are the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            }
        }

        test("oppgave ferdigstilles selv om sak ikke finnes") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            } throws SakEllerOppgaveFinnesIkkeException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            }
        }

        test("sak og oppgave som ikke finnes håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            } throws SakEllerOppgaveFinnesIkkeException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("We are the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            }
        }

        test("ukjent feil for sak håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            } throws NullPointerException("It's just a flesh wound")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()
            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            }
        }

        test("ukjent feil for oppgave håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
            } just Runs

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            } throws NullPointerException("We are no longer the knights who say 'Ni!'")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
            }
        }

        test("ny, selvbestemt inntektsmelding ignoreres") {
            val innkommendeMelding = innkommendeMelding(EventName.SELVBESTEMT_IM_LAGRET)
            val innkommendeMeldingMap =
                innkommendeMelding
                    .toMap()
                    .plusData(
                        Key.SELVBESTEMT_INNTEKTSMELDING to
                            mockInntektsmeldingV1()
                                .copy(
                                    type = innkommendeMelding.imType,
                                    aarsakInnsending = AarsakInnsending.Ny,
                                ).toJson(Inntektsmelding.serializer()),
                    )

            testRapid.sendJson(innkommendeMeldingMap)

            testRapid.inspektør.size shouldBeExactly 0

            coVerify(exactly = 0) {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
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
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), hardDeleteOm = any())
                    mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(eventName: EventName = EventName.FORESPOERSEL_BESVART): FerdigstillMelding {
    val imType =
        when (eventName) {
            EventName.INNTEKTSMELDING_SKJEMA_LAGRET, EventName.FORESPOERSEL_BESVART -> Inntektsmelding.Type.Forespurt(UUID.randomUUID())
            EventName.SELVBESTEMT_IM_LAGRET -> Inntektsmelding.Type.Selvbestemt(UUID.randomUUID())
            else -> fail("Melding har ugyldig eventnavn.")
        }

    return FerdigstillMelding(
        eventName = eventName,
        kontekstId = UUID.randomUUID(),
        imType = imType,
    )
}

private fun FerdigstillMelding.toMap(): Map<Key, JsonElement> {
    val dataField =
        when (eventName) {
            EventName.INNTEKTSMELDING_SKJEMA_LAGRET -> {
                Key.SKJEMA_INNTEKTSMELDING to
                    mockSkjemaInntektsmelding()
                        .copy(
                            forespoerselId = imType.id,
                        ).toJson(SkjemaInntektsmelding.serializer())
            }

            EventName.SELVBESTEMT_IM_LAGRET -> {
                Key.SELVBESTEMT_INNTEKTSMELDING to
                    mockInntektsmeldingV1()
                        .copy(
                            type = imType,
                            aarsakInnsending = AarsakInnsending.Endring,
                        ).toJson(Inntektsmelding.serializer())
            }

            EventName.FORESPOERSEL_BESVART -> {
                Key.FORESPOERSEL_ID to imType.id.toJson()
            }

            else -> {
                fail("Melding har ugyldig eventnavn.")
            }
        }

    return mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to mapOf(dataField).toJson(),
    )
}

private fun forventetUtgaaendeMelding(innkommendeMelding: FerdigstillMelding): Map<Key, JsonElement> {
    val idKey =
        if (innkommendeMelding.imType.erForespurt()) {
            Key.FORESPOERSEL_ID
        } else {
            Key.SELVBESTEMT_ID
        }

    return mapOf(
        Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_FERDIGSTILT.toJson(),
        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
        idKey to innkommendeMelding.imType.id.toJson(),
    )
}

private fun forventetFail(innkommendeMelding: FerdigstillMelding): Fail =
    Fail(
        feilmelding = "Klarte ikke ferdigstille sak og/eller oppgave for inntektmelding.",
        kontekstId = innkommendeMelding.kontekstId,
        utloesendeMelding = innkommendeMelding.toMap(),
    )
