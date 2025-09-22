package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentDataTilSakOgOppgaveServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentDataTilSakOgOppgaveService(it),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
        }

        test("henter data for å opprette sak og oppgave") {

            testRapid.sendJson(Mock.steg0())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(Mock.steg1())

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER

            testRapid.sendJson(Mock.steg2())

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED.toJson(),
                    Key.KONTEKST_ID to Mock.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                            Key.FORESPOERSEL to Mock.forespoersel.toJson(Forespoersel.serializer()),
                            Key.SYKMELDT to
                                Mock.personer.values
                                    .first()
                                    .toJson(Person.serializer()),
                            Key.VIRKSOMHET to
                                Mock.orgnrMedNavn.values
                                    .first()
                                    .toJson(),
                            Key.SKAL_HA_PAAMINNELSE to Mock.SKAL_HA_PAAMINNELSE.toJson(Boolean.serializer()),
                        ).toJson(),
                )
        }

        test("publiserer ingen melding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "He's just a little horse",
                    eventName = EventName.FORESPOERSEL_MOTTATT,
                    behovType = BehovType.HENT_VIRKSOMHET_NAVN,
                )

            testRapid.sendJson(Mock.steg0())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
        }
    })

private object Mock {
    const val SKAL_HA_PAAMINNELSE = true
    val forespoersel = mockForespoersel()
    val kontekstId: UUID = forespoersel.vedtaksperiodeId
    val forespoerselId: UUID = UUID.randomUUID()
    val orgnrMedNavn = mapOf(forespoersel.orgnr to "Kåre Conradis Kål og Kålrabi")
    val personer = listOf(forespoersel.fnr).associateWith { Person(it, "Kåre Conradi") }

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.FORESPOERSEL to forespoersel.toJson(Forespoersel.serializer()),
                    Key.SKAL_HA_PAAMINNELSE to SKAL_HA_PAAMINNELSE.toJson(Boolean.serializer()),
                ).toJson(),
        )

    fun steg1(): Map<Key, JsonElement> =
        steg0().plusData(
            Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
        )

    fun steg2(): Map<Key, JsonElement> =
        steg1().plusData(
            Key.PERSONER to personer.toJson(personMapSerializer),
        )
}
