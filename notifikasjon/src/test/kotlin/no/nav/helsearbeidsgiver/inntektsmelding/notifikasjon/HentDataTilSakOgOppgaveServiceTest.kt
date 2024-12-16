package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentDataTilSakOgOppgaveServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        ServiceRiverStateless(
            HentDataTilSakOgOppgaveService(testRapid),
        ).connect(testRapid)

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
                    Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
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
    val transaksjonId: UUID = forespoersel.vedtaksperiodeId
    val forespoerselId: UUID = UUID.randomUUID()
    val orgnrMedNavn = mapOf(forespoersel.orgnr to "Kåre Conradis Kål og Kålrabi")
    val personer = listOf(forespoersel.fnr).associateWith { Person(it, "Kåre Conradi") }

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
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
