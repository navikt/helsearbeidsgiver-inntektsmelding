package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class SakFerdigLoeserTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
        val mockLinkUrl = "mock-url"
        SakFerdigLoeser(testRapid, mockAgNotifikasjonKlient, mockLinkUrl)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved besvart forespørsel så ferdigstilles saken") {
            val sakId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.SAK_ID to sakId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SAK_FERDIGSTILT.toJson(),
                    Key.SAK_ID to sakId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
            }
        }

        test("Ved besvart forespørsel med gammel merkelapp så ferdigstilles saken") {
            val sakId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP, any(), any(), any())
            } throws NullPointerException("'Tis but a scratch")

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.SAK_ID to sakId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SAK_FERDIGSTILT.toJson(),
                    Key.SAK_ID to sakId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Mottatt – Se kvittering eller korriger inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
            }
        }

        test("Ved besvart forespørsel på sak som ikke finnes så ignoreres exception") {
            val sakId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP, any(), any(), any())
            } throws NullPointerException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP_GAMMEL, any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("It's just a flesh wound")

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.SAK_ID to sakId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SAK_FERDIGSTILT.toJson(),
                    Key.SAK_ID to sakId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding", any(), any(), any())
            }
        }

        test("Ukjent feil håndteres") {
            val sakId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP, any(), any(), any())
            } throws NullPointerException("'Tis but a scratch")

            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), NotifikasjonTekst.MERKELAPP_GAMMEL, any(), any(), any())
            } throws NullPointerException("It's just a flesh wound")

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.SAK_ID to sakId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 0

            coVerifySequence {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any())
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding", any(), any(), any())
            }
        }
    })
