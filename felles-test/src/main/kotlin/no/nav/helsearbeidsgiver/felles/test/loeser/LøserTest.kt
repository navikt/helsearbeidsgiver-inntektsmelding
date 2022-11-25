package no.nav.helsearbeidsgiver.felles.test.loeser

import io.mockk.every
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.felles.test.mock.mockObject

abstract class LøserTest {
    val testRapid = TestRapid()

    fun withTestRapid(initLøser: () -> Løser): Løser =
        mockObject(RapidApplication) {
            every { RapidApplication.create(any()) } returns testRapid

            initLøser()
        }
}
