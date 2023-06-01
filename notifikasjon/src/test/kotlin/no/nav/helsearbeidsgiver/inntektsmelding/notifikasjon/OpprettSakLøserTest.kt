@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET,
                Key.BEHOV.str to BehovType.OPPRETT_SAK.name,
                DataFelt.ARBEIDSTAKER_INFORMASJON.str to customObjectMapper().valueToTree(PersonDato(NAVN, FØDSELSDATO)),
                Key.FORESPOERSEL_ID.str to "uuid-abc",
                DataFelt.ORGNRUNDERENHET.str to "org-456",
                Key.IDENTITETSNUMMER.str to "12345678901"
            )
        )
        assertNotNull(resultat)
        resultat.contains(Key.DATA.str)
        resultat.contains(DataFelt.SAK_ID.str)
        val sakId = resultat.get(DataFelt.SAK_ID.str).asText()
        assertEquals(sakId, "ID")
    }

    private fun sendMelding(melding: Map<String, Any>): JsonNode {
        rapid.reset()
        rapid.sendTestMessage(om.writeValueAsString(melding))
        return rapid.inspektør.message(0)
    }
}
