package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
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

            publisert shouldContainKey Key.UUID

            publisert.minus(Key.UUID) shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPØRSEL_MOTTATT.toJson(EventName.serializer()),
                    Key.BEHOV to BehovType.LAGRE_FORESPOERSEL.toJson(BehovType.serializer()),
                    Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to innkommendeMelding.orgnr.toJson(),
                    Key.IDENTITETSNUMMER to innkommendeMelding.fnr.toJson(),
                )
        }
    })

private fun mockInnkommendeMelding(): Melding =
    Melding(
        notisType = Pri.NotisType.FORESPØRSEL_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        orgnr = Orgnr.genererGyldig(),
        fnr = Fnr.genererGyldig(),
    )

private fun Melding.toMap(): Map<Pri.Key, JsonElement> =
    mapOf(
        Pri.Key.NOTIS to notisType.toJson(Pri.NotisType.serializer()),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.ORGNR to orgnr.toJson(),
        Pri.Key.FNR to fnr.toJson(),
    )
