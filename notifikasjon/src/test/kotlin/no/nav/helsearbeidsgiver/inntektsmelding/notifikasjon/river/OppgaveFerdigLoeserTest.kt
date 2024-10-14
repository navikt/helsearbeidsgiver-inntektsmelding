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
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OppgaveFerdigLoeserTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
        val mockLinkUrl = "mock-url"

        OppgaveFerdigLoeser(testRapid, mockAgNotifikasjonKlient, mockLinkUrl)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved besvart forespørsel så ferdigstilles oppgaven") {
            val oppgaveId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.OPPGAVE_FERDIGSTILT.toJson(),
                    Key.OPPGAVE_ID to oppgaveId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )

            coVerifySequence {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
            }
        }

        test("Ved besvart forespørsel med gammel merkelapp så ferdigstilles oppgaven") {
            val oppgaveId = UUID.randomUUID().toString()
            val forespoerselId = UUID.randomUUID()
            val transaksjonId = UUID.randomUUID()

            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(any(), NotifikasjonTekst.MERKELAPP, any())
            } throws NullPointerException("We are the knights who say 'Ni!'")

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.OPPGAVE_FERDIGSTILT.toJson(),
                    Key.OPPGAVE_ID to oppgaveId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                )

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.oppgaveUtfoertByEksternIdV2(
                    eksternId = forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    nyLenke = "$mockLinkUrl/im-dialog/kvittering/$forespoerselId",
                )
            }
        }
    })
