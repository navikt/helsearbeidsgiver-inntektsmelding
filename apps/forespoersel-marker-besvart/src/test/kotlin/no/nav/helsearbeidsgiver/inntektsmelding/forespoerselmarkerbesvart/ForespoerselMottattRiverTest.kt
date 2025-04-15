package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class ForespoerselMottattRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        ForespoerselMottattRiver().connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved notis om mottatt forespørsel publiseres behov om notifikasjon") {
            val innkommendeMelding = mockInnkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.KONTEKST_ID

            publisert.minus(Key.KONTEKST_ID) shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_MOTTATT.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                            Key.FORESPOERSEL to innkommendeMelding.forespoerselFraBro.toForespoersel().toJson(Forespoersel.serializer()),
                            Key.SKAL_HA_PAAMINNELSE to innkommendeMelding.skalHaPaaminnelse.toJson(Boolean.serializer()),
                        ).toJson(),
                )
        }
    })

private fun mockInnkommendeMelding(): MottattMelding =
    MottattMelding(
        notisType = Pri.NotisType.FORESPØRSEL_MOTTATT,
        kontekstId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        forespoerselFraBro = Mock.forespoerselFraBro,
        skalHaPaaminnelse = true,
    )

private fun MottattMelding.toMap(): Map<Pri.Key, JsonElement> =
    mapOf(
        Pri.Key.NOTIS to notisType.toJson(Pri.NotisType.serializer()),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.FORESPOERSEL to forespoerselFraBro.toJson(ForespoerselFraBro.serializer()),
        Pri.Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
    )

object Mock {
    private val orgnr = Orgnr.genererGyldig()
    val forespoerselFraBro =
        ForespoerselFraBro(
            orgnr = orgnr,
            fnr = Fnr.genererGyldig(),
            vedtaksperiodeId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            sykmeldingsperioder = listOf(2.januar til 16.januar),
            egenmeldingsperioder = listOf(1.januar til 1.januar),
            bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
            forespurtData = mockForespurtData(),
            erBesvart = false,
        )
}
