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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentDataTilPaaminnelseServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        ServiceRiverStateless(
            HentDataTilPaaminnelseService(testRapid),
        ).connect(testRapid)

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
                    Key.KONTEKST_ID to HentDataTilPaaminnelseServiceMock.transaksjonId.toJson(),
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
    val transaksjonId: UUID = forespoersel.vedtaksperiodeId
    val forespoerselId: UUID = UUID.randomUUID()
    val orgnrMedNavn = mapOf(forespoersel.orgnr.let(::Orgnr) to "Kåre Conradis Kål og Kålrabi")

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.MANUELL_ENDRE_PAAMINNELSE.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
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
