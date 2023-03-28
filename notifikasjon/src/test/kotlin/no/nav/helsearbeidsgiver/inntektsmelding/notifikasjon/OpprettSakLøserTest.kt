@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class OpprettSakLøserTest {

    private val rapid = TestRapid()
    private var løser: OpprettSakLøser
    private val om = customObjectMapper()
    var arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

    init {
        løser = OpprettSakLøser(rapid, arbeidsgiverNotifikasjonKlient, "root")
        mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNySakKt")
    }

    @Test
    @Disabled
    fun `skal opprette sak med fullt navn`() {
        sendMelding(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT.name,
                Key.LØSNING.str to mapOf<String, Any>(
                    "FULLT_NAVN" to NavnLøsning(value = "Rosa damesykkel")
                ),
                Key.UUID.str to "uuid-abc",
                Key.ORGNRUNDERENHET.str to "org-456",
                Key.IDENTITETSNUMMER.str to "12345678901"
            )
        )
        coVerify {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                "uuid-abc",
                "Inntektsmelding",
                "org-456",
                "Inntektsmelding for Rosa damesykkel: f. 678901",
                "root/im-dialog/uuid-abc",
                "NAV trenger inntektsmelding",
                "P5M"
            )
        }
    }

    private fun sendMelding(melding: Map<String, Any>): JsonNode {
        rapid.reset()
        rapid.publish(om.writeValueAsString(melding))
        return rapid.inspektør.message(0)
    }
}
