package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangskontrollIT : EndToEndTest() {

    val INNLOGGET_FNR = "fnr-456"
    val FORESPØRSEL_ID_HAR_TILGANG = UUID.randomUUID().toString()
    val FORESPØRSEL_ID_IKKE_TILGANG = UUID.randomUUID().toString()
    val FORESPØRSEL_ID_FINNES_IKKE = UUID.randomUUID().toString()
    val ORGNR_HAR_TILGANG = "org-456"
    val ORGNR_IKKE_TILGANG = "org-789"

    @BeforeAll
    fun before() {
        repository.lagreForespørsel(FORESPØRSEL_ID_HAR_TILGANG, ORGNR_HAR_TILGANG)
        repository.lagreForespørsel(FORESPØRSEL_ID_IKKE_TILGANG, ORGNR_IKKE_TILGANG)
        coEvery {
            altinnClient.harRettighetForOrganisasjon(INNLOGGET_FNR, ORGNR_IKKE_TILGANG)
        } returns false
        coEvery {
            altinnClient.harRettighetForOrganisasjon(INNLOGGET_FNR, ORGNR_HAR_TILGANG)
        } returns true
    }

    @Test
    fun `skal få melding om at forespørsel ikke finnes`() {
        results.clear()
        producer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_FINNES_IKKE)
        Thread.sleep(4000)
        with(getMessage(0)) {
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
            assertEquals(
                "Fant ikke forespørselId $FORESPØRSEL_ID_FINNES_IKKE",
                get(Key.LØSNING.str).get(BehovType.HENT_IM_ORGNR.name).get("error").get("melding").asText()
            )
        }
    }

    @Test
    fun `skal bli nektet tilgang`() {
        results.clear()
        producer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_IKKE_TILGANG)
        Thread.sleep(4000)
        with(getMessage(3)) {
            assertNotNull(get(Key.LØSNING.str))
            assertEquals(
                "Du har ikke rettigheter til å se på denne.",
                get(Key.LØSNING.str).get(BehovType.TILGANGSKONTROLL.name).get("error").get("melding").asText()
            )
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
        }
    }

    @Test
    fun `hent ut preutfylte data for forespørsel`() {
        results.clear()
        producer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_HAR_TILGANG)
        Thread.sleep(4000)
        with(getMessage(1)) {
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
        }
    }
}
