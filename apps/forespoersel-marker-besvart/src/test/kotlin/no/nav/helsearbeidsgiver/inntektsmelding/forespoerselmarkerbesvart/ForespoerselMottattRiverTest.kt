package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.ForespoerselFraBro
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.test.mockForespoerselFraBro
import no.nav.hag.simba.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselMottattRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                ForespoerselMottattRiver(),
            )
        }

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
                            Key.FORESPOERSEL to innkommendeMelding.forespoerselFraBro.toForespoersel().toJson(),
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
        forespoerselFraBro = mockForespoerselFraBro(),
        skalHaPaaminnelse = true,
    )

private fun MottattMelding.toMap(): Map<Pri.Key, JsonElement> =
    mapOf(
        Pri.Key.NOTIS to notisType.toJson(Pri.NotisType.serializer()),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.FORESPOERSEL to forespoerselFraBro.toJson(ForespoerselFraBro.serializer()),
        Pri.Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
    )
