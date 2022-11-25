package no.nav.helsearbeidsgiver.felles.test.loeser

import io.mockk.every
import io.mockk.mockkObject
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.loeser.Løser

abstract class LøserTest {
    fun mockRapid(initLøser: () -> Løser): TestRapid {
        val testRapid = TestRapid()

        mockkObject(RapidApplication) {
            every { RapidApplication.create(any()) } returns testRapid

            initLøser()
        }

        return testRapid
    }
}
