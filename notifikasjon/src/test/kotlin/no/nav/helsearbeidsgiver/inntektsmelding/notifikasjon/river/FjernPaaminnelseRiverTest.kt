package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class FjernPaaminnelseRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockagNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        FjernPaaminnelseRiver(mockagNotifikasjonKlient)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("fjern påminnelse for forespørsel") {
            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 0
        }
    })
