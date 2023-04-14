@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OpprettSakLøserTest {

    private val rapid = TestRapid()
    private var løser: OpprettSakLøser
    private val om = customObjectMapper()
    val placeholderSak = mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNySakKt")
    private var arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()
    private var FØDSELSDATO = LocalDate.of(2020, 1, 5)
    private val NAVN = "Rosa damesykkel"

    init {
        løser = OpprettSakLøser(rapid, arbeidsgiverNotifikasjonKlient, "root")
    }

    @Test
    fun `skal opprette sak med fullt navn`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(
                "uuid-abc",
                "Inntektsmelding",
                "org-456",
                "Inntektsmelding for $NAVN: f. 050120",
                "root/im-dialog/uuid-abc",
                "NAV trenger inntektsmelding",
                "P5M"
            )
        } answers { "ID" }
        val resultat = sendMelding(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT.name,
                Key.BEHOV.str to listOf(BehovType.FULLT_NAVN.name),
                Key.LØSNING.str to mapOf<String, Any>(
                    BehovType.FULLT_NAVN.name to NavnLøsning(value = PersonDato(NAVN, FØDSELSDATO))
                ),
                Key.UUID.str to "uuid-abc",
                Key.ORGNRUNDERENHET.str to "org-456",
                Key.IDENTITETSNUMMER.str to "12345678901"
            )
        )
        assertNotNull(resultat)
    }

    private fun sendMelding(melding: Map<String, Any>): JsonNode {
        rapid.reset()
        rapid.sendTestMessage(om.writeValueAsString(melding))
        return rapid.inspektør.message(0)
    }
}
