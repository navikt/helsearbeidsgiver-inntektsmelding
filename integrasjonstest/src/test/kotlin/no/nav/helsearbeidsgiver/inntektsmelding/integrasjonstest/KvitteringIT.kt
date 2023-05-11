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
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KvitteringIT : EndToEndTest() {

    private val UGYLDIG_FORESPPØRSEL_ID = "ugyldig-forespørsel"
    private val GYLDIG_FORESPØRSEL_ID = "gyldig-forespørsel"
    private val ORGNR = "987"
    private val INNTEKTSMELDING_DOKUMENT = MockInntektsmeldingDokument()
    private val INNTEKTSMELDING_NOT_FOUND = "{}"

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val transactionId = UUID.randomUUID().toString()
        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.UUID.str to transactionId,
                Key.FORESPOERSEL_ID.str to UGYLDIG_FORESPPØRSEL_ID
            )
        )
        Thread.sleep(5000)
        assertNotNull(meldinger)
        with(filter(EventName.KVITTERING_REQUESTED, datafelt = DataFelt.INNTEKTSMELDING_DOKUMENT).first()) {
            // Skal ikke finne inntektsmeldingdokument - men en dummy payload
            assertEquals(INNTEKTSMELDING_NOT_FOUND, get(Key.INNTEKTSMELDING_DOKUMENT.str).asText())
            assertEquals(transactionId, get(Key.UUID.str).asText())
        }
    }

    @Test
    fun `skal hente data til kvittering`() {
        val transactionId = UUID.randomUUID().toString()
        meldinger.clear()
        forespoerselRepository.lagreForespørsel(GYLDIG_FORESPØRSEL_ID, ORGNR)
        imoRepository.lagreInntektsmeldng(GYLDIG_FORESPØRSEL_ID, INNTEKTSMELDING_DOKUMENT)
        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.UUID.str to transactionId,
                Key.FORESPOERSEL_ID.str to GYLDIG_FORESPØRSEL_ID
            )
        )
        Thread.sleep(5000)
        assertNotNull(meldinger)
        with(filter(EventName.KVITTERING_REQUESTED, datafelt = DataFelt.INNTEKTSMELDING_DOKUMENT).first()) {
            assertNotNull(get(Key.INNTEKTSMELDING_DOKUMENT.str))
            // Skal finne inntektsmeldingdokumentet
            assertNotEquals(INNTEKTSMELDING_NOT_FOUND, get(Key.INNTEKTSMELDING_DOKUMENT.str))
            assertEquals(transactionId, get(Key.UUID.str).asText())
        }
    }
}
