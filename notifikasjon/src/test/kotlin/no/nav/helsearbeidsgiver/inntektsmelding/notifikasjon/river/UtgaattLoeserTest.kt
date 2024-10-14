package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coVerifySequence
import io.mockk.mockk
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class UtgaattLoeserTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)

        UtgaattLoeser(testRapid, mockAgNotifikasjonKlient, Mock.linkUrl)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved forkastet forespørsel med forespørsel-ID settes oppgaven til utgått") {
            val expected =
                mapOf(
                    Key.EVENT_NAME to EventName.OPPGAVE_OG_SAK_UTGAATT.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                )

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.toJson(),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.UUID to Mock.transaksjonId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val actual = testRapid.firstMessage().toMap()

            actual shouldBe expected
            coVerifySequence {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = Mock.forespoerselId.toString(),
                    nyLenke = "${Mock.linkUrl}/im-dialog/utgatt",
                )
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = Mock.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    tidspunkt = null,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.linkUrl}/im-dialog/utgatt",
                )
            }
        }
    })

private object Mock {
    val linkUrl = "enSlagsUrl"
    val forespoerselId = UUID.randomUUID()
    val transaksjonId = UUID.randomUUID()
}
