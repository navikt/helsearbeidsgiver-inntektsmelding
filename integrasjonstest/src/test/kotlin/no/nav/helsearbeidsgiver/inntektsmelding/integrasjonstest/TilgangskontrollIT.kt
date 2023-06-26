package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled
class TilgangskontrollIT : EndToEndTest() {

    private val INNLOGGET_FNR = "fnr-456"
    private val FORESPØRSEL_ID_HAR_TILGANG = UUID.randomUUID().toString()
    private val FORESPØRSEL_ID_IKKE_TILGANG = UUID.randomUUID().toString()
    private val FORESPØRSEL_ID_FINNES_IKKE = UUID.randomUUID().toString()
    private val ORGNR_HAR_TILGANG = "org-456"
    private val ORGNR_IKKE_TILGANG = "org-789"

    @BeforeAll
    fun beforeAll() {
        forespoerselRepository.lagreForespørsel(FORESPØRSEL_ID_HAR_TILGANG, ORGNR_HAR_TILGANG)
        forespoerselRepository.lagreForespørsel(FORESPØRSEL_ID_IKKE_TILGANG, ORGNR_IKKE_TILGANG)
    }

    @BeforeEach
    fun beforeEach() {
        coEvery {
            altinnClient.harRettighetForOrganisasjon(INNLOGGET_FNR, ORGNR_IKKE_TILGANG)
        } returns false
        coEvery {
            altinnClient.harRettighetForOrganisasjon(INNLOGGET_FNR, ORGNR_HAR_TILGANG)
        } returns true
    }

    @Test
    fun `skal få melding om at forespørsel ikke finnes`() {
        tilgangProducer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_FINNES_IKKE)
        Thread.sleep(4000)
        with(filter(EventName.HENT_PREUTFYLT, BehovType.HENT_IM_ORGNR, løsning = true).first()) {
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
            assertEquals(
                "Fant ikke forespørselId $FORESPØRSEL_ID_FINNES_IKKE",
                get(Key.LØSNING.str).get(BehovType.HENT_IM_ORGNR.name).get("error").get("melding").asText()
            )
        }
    }

    @Test
    fun `skal bli nektet tilgang`() {
        tilgangProducer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_IKKE_TILGANG)
        Thread.sleep(4000)
        with(filter(EventName.HENT_PREUTFYLT, BehovType.TILGANGSKONTROLL, løsning = true).first()) {
            assertNotNull(get(Key.LØSNING.str))
            val løsning: TilgangskontrollLøsning = get(Key.LØSNING.str).get(BehovType.TILGANGSKONTROLL.name).toJsonElement().fromJson(
                TilgangskontrollLøsning.serializer()
            )
            assertEquals(Tilgang.IKKE_TILGANG, løsning.value)
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
        }
    }

    @Test
    fun `skal få tilgang`() {
        tilgangProducer.publish(INNLOGGET_FNR, FORESPØRSEL_ID_HAR_TILGANG)
        Thread.sleep(6000)
        assertNotNull(messages)
        with(filter(EventName.HENT_PREUTFYLT, BehovType.TILGANGSKONTROLL, løsning = true).first()) {
            assertEquals(BehovType.HENT_IM_ORGNR.name, get(Key.BEHOV.str)[0].asText())
        }
    }
}
