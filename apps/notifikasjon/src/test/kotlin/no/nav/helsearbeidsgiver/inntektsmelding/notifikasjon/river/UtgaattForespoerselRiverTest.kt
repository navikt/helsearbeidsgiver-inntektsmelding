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
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.Mock.toMap
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakUtgaattLevetid
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class UtgaattForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                UtgaattForespoerselRiver(Mock.LINK_URL, mockAgNotifikasjonKlient),
            )
        }

        beforeTest {
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
                    statusTekst = "Avbrutt av Nav",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                    hardDeleteOm = sakUtgaattLevetid,
                )
            }
        }

        test("Hvis oppgaveUtgaattByEksternId feiler med SakEllerOppgaveFinnesIkkeException skal saken avbrytes likevel") {
            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotifikasjonKlient")

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
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = "Inntektsmelding sykepenger",
                    status = SaksStatus.FERDIG,
                    statusTekst = "Avbrutt av Nav",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                    hardDeleteOm = sakUtgaattLevetid,
                )
            }
        }

        test("Hvis avbrytSak feiler med SakEllerOppgaveFinnesIkkeException skal oppgaven settes til utgått likevel") {
            coEvery {
                mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveFinnesIkkeException("Feil fra agNotifikasjonKlient")

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
                    statusTekst = "Avbrutt av Nav",
                    nyLenke = "${Mock.LINK_URL}/im-dialog/utgatt",
                    hardDeleteOm = sakUtgaattLevetid,
                )
            }
        }

        test("Ved feil ved oppgaveUtgaattByEksternId skal saken ikke avbrytes") {
            coEvery {
                mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(any(), any(), any())
            } throws Exception("Feil fra agNotifikasjonKlient")

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
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket event" to Pair(Key.EVENT_NAME, EventName.FORESPOERSEL_BESVART.toJson()),
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_INNTEKT.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.forventetFail(Mock.innkommendeMelding()).toJson(Fail.serializer())),
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
                    mockAgNotifikasjonKlient.oppgaveUtgaattByEksternId(any(), any(), any())
                    mockAgNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any(), any(), any(), any())
                }
            }
        }
    })

private object Mock {
    const val LINK_URL = "enSlagsUrl"

    fun innkommendeMelding(): UtgaattForespoerselMelding =
        UtgaattForespoerselMelding(
            eventName = EventName.FORESPOERSEL_FORKASTET,
            kontekstId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
        )

    fun UtgaattForespoerselMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )

    fun forventetUtgaaendeMelding(innkommendeMelding: UtgaattForespoerselMelding): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_UTGAATT.toJson(),
            Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
        )

    fun forventetFail(innkommendeMelding: UtgaattForespoerselMelding): Fail =
        Fail(
            feilmelding = "Klarte ikke sette oppgave til utgått og/eller avbryte sak for forespurt inntektmelding.",
            kontekstId = innkommendeMelding.kontekstId,
            utloesendeMelding = innkommendeMelding.toMap(),
        )
}
