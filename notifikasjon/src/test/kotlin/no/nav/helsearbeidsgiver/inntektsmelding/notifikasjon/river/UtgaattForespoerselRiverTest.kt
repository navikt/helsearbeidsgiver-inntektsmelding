package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class UtgaattForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)

        UtgaattForespoerselRiver(Mock.LINK_URL, mockAgNotifikasjonKlient).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved forkastet forespørsel med forespørsel-ID settes oppgaven til utgått og sak til ferdig") {
            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    tidspunkt = null,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
            }
        }

        test("Hvis oppdatering av oppgave og sak feiler med SakEllerOppgaveFinnesIkkeException skal oppgaven og saken oppdateres med gammel merkelapp") {
            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId("Inntektsmelding sykepenger", any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotikitasjonKlient")
            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), "Inntektsmelding sykepenger", any(), any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotikitasjonKlient")

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
            }
        }

        test("Hvis oppgaveUtgaattByEksternId med ny og gammel merkelapp feiler med SakEllerOppgaveFinnesIkkeException skal saken avbrytes likevel") {
            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotikitasjonKlient")

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler ikke
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
            }
        }

        test("Hvis avbrytSak med ny og gammel merkelapp feiler med SakEllerOppgaveFinnesIkkeException skal oppgaven settes til utgått likevel") {
            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotikitasjonKlient")

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding)

            coVerifySequence {
                // Feiler ikke
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av NAV",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
            }
        }

        test("Ved feil ved oppgaveUtgaattByEksternId med ny og gammel merkelapp skal saken ikke avbrytes") {
            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(any(), any(), any())
            } throws Exception("Feil fra agNotikitasjonKlient")

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding sykepenger",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
                // Feiler
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(
                    merkelapp = "Inntektsmelding",
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                )
            }
        }
    })

private object Mock {
    const val LINK_URL = "enSlagsUrl"

    fun innkommendeMelding(): UtgaattForespoerselMelding =
        UtgaattForespoerselMelding(
            eventName = EventName.FORESPOERSEL_FORKASTET,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
        )

    fun UtgaattForespoerselMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )

    fun forventetUtgaaendeMelding(innkommendeMelding: UtgaattForespoerselMelding): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_UTGAATT.toJson(),
            Key.UUID to innkommendeMelding.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
        )

    fun forventetFail(innkommendeMelding: UtgaattForespoerselMelding): Fail =
        Fail(
            feilmelding = "Klarte ikke sette oppgave til utgått og/eller avbryte sak for forespurt inntektmelding.",
            event = innkommendeMelding.eventName,
            transaksjonId = innkommendeMelding.transaksjonId,
            forespoerselId = innkommendeMelding.forespoerselId,
            utloesendeMelding = innkommendeMelding.toMap().toJson(),
        )
}
