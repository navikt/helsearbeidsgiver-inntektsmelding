package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.rr.test.firstMessage
import no.nav.helsearbeidsgiver.felles.rr.test.message
import no.nav.helsearbeidsgiver.felles.rr.test.mockConnectToRapid
import no.nav.helsearbeidsgiver.felles.rr.test.sendJson
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentDataTilPaaminnelseServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentDataTilPaaminnelseService(it),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
        }

        test("henter data for å endre påminnelse for oppgave") {

            testRapid.sendJson(HentDataTilPaaminnelseServiceMock.steg0())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(HentDataTilPaaminnelseServiceMock.steg1())

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(HentDataTilPaaminnelseServiceMock.steg2())

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.OPPGAVE_ENDRE_PAAMINNELSE_REQUESTED.toJson(),
                    Key.KONTEKST_ID to HentDataTilPaaminnelseServiceMock.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to HentDataTilPaaminnelseServiceMock.forespoerselId.toJson(),
                            Key.FORESPOERSEL to HentDataTilPaaminnelseServiceMock.forespoersel.toJson(Forespoersel.serializer()),
                            Key.VIRKSOMHET to
                                HentDataTilPaaminnelseServiceMock.orgnrMedNavn.values
                                    .first()
                                    .toJson(),
                        ).toJson(),
                )
        }

        test("publiserer ingen melding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "He's just a little horse",
                    eventName = EventName.MANUELL_ENDRE_PAAMINNELSE,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(HentDataTilPaaminnelseServiceMock.steg0())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
        }
    })

private object HentDataTilPaaminnelseServiceMock {
    val forespoersel = mockForespoersel()
    val kontekstId: UUID = forespoersel.vedtaksperiodeId
    val forespoerselId: UUID = UUID.randomUUID()
    val orgnrMedNavn = mapOf(forespoersel.orgnr to "Kåre Conradis Kål og Kålrabi")

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.MANUELL_ENDRE_PAAMINNELSE.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ).toJson(),
        )

    fun steg1(): Map<Key, JsonElement> =
        steg0().plusData(
            Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
        )

    fun steg2(): Map<Key, JsonElement> =
        steg1().plusData(
            Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
        )
}
