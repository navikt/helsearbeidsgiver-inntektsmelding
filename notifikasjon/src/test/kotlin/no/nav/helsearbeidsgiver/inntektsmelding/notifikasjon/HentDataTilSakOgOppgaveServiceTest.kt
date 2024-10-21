package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.PersonDato
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.firstMessage().lesBehov() shouldBe BehovType.LAGRE_FORESPOERSEL
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(Mock.steg1())

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).lesBehov() shouldBe BehovType.HENT_PERSONER

            testRapid.sendJson(Mock.steg2())

            testRapid.inspektør.size shouldBeExactly 5

            testRapid.message(3).toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
                    Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to Mock.forespoerelId.toJson(),
                    Key.ORGNRUNDERENHET to Mock.orgnr.toJson(),
                    Key.ARBEIDSTAKER_INFORMASJON to
                        PersonDato(
                            navn = "Kåre Conradi",
                            fødselsdato = 22.april,
                            ident = Mock.fnr.verdi,
                        ).toJson(PersonDato.serializer()),
                    Key.PERSONER to
                        Mock.personer.values
                            .first()
                            .toJson(Person.serializer()),
                )

            testRapid.message(4).toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
                    Key.BEHOV to BehovType.OPPRETT_OPPGAVE.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to Mock.forespoerelId.toJson(),
                    Key.ORGNRUNDERENHET to Mock.orgnr.toJson(),
                    Key.VIRKSOMHET to
                        Mock.orgnrMedNavn.values
                            .first()
                            .toJson(),
                )
        }

        test("publiserer ingen melding ved feil") {
            val fail =
                Fail(
                    feilmelding = "He's just a little horse",
                    event = EventName.FORESPOERSEL_MOTTATT,
                    transaksjonId = Mock.transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                            ),
                        ),
                )

            testRapid.sendJson(Mock.steg0())

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.firstMessage().lesBehov() shouldBe BehovType.LAGRE_FORESPOERSEL
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 2
        }
    })

private object Mock {
    val transaksjonId: UUID = UUID.randomUUID()
    val forespoerelId: UUID = UUID.randomUUID()
    val orgnr = Orgnr.genererGyldig()
    val fnr = Fnr.genererGyldig()
    val orgnrMedNavn = mapOf(orgnr to "Kåre Conradis Kål og Kålrabi")
    val personer = mapOf(fnr to Person(fnr, "Kåre Conradi", 22.april))

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerelId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.FNR to fnr.toJson(),
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
