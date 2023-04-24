package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.MockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KvitteringIT : EndToEndTest() {

    private val UGYLDIG_FORESPPØRSEL_ID = "ugyldig-forespørsel"
    private val GYLDIG_FORESPØRSEL_ID = "gyldig-forespørsel"
    private val ORGNR = "987"
    private val INNTEKTSMELDING_DOKUMENT = MockInntektsmeldingDokument()
    private val INNTEKTSMELDING_NOT_FOUND = "{}"
    private val transactionId = "123456"

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.UUID.str to UGYLDIG_FORESPPØRSEL_ID,
                Key.INITIATE_ID.str to transactionId
            )
        )
        Thread.sleep(5000)
        assertNotNull(meldinger)
        with(filter(EventName.KVITTERING_REQUESTED, datafelt = DataFelt.INNTEKTSMELDING_DOKUMENT).first()) {
            // Skal ikke finne inntektsmeldingdokument - men en dummy payload
            assertEquals(INNTEKTSMELDING_NOT_FOUND, get(Key.INNTEKTSMELDING_DOKUMENT.str).asText())
            assertEquals(UGYLDIG_FORESPPØRSEL_ID, get(Key.UUID.str).asText())
        }
    }

    @Test
    fun `skal hente data til kvittering`() {
        meldinger.clear()
        forespoerselRepository.lagreForespørsel(GYLDIG_FORESPØRSEL_ID, ORGNR)
        imoRepository.lagreInntektsmeldng(GYLDIG_FORESPØRSEL_ID, INNTEKTSMELDING_DOKUMENT)
        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.UUID.str to GYLDIG_FORESPØRSEL_ID,
                Key.INITIATE_ID.str to transactionId
            )
        )
        Thread.sleep(5000)
        assertNotNull(meldinger)
        with(filter(EventName.KVITTERING_REQUESTED, datafelt = DataFelt.INNTEKTSMELDING_DOKUMENT).first()) {
            assertNotNull(get(Key.INNTEKTSMELDING_DOKUMENT.str))
            // Skal finne inntektsmeldingdokumentet
            assertNotEquals(INNTEKTSMELDING_NOT_FOUND, get(Key.INNTEKTSMELDING_DOKUMENT.str))
            assertEquals(GYLDIG_FORESPØRSEL_ID, get(Key.UUID.str).asText())
        }
    }
}
