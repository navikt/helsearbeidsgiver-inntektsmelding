package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class JournalfoerInntektsmeldingMottattListenerTest {
    val rapid = TestRapid()
    var inntektMottat: JournalfoerInntektsmeldingMottattListener

    init {
        inntektMottat = JournalfoerInntektsmeldingMottattListener(rapid)
    }

    @Test
    fun publisererEventOgBehovVedMottattInntektsmelding() {
        val request = InnsendingRequest(
            "",
            "",
            emptyList(),
            emptyList(),
            emptyList(),
            LocalDate.now(),
            emptyList(),
            Inntekt(
                bekreftet = true,
                500.0.toBigDecimal(),
                Bonus(),
                true
            ),
            FullLonnIArbeidsgiverPerioden(
                true
            ),
            Refusjon(
                true
            ),
            emptyList(),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.NY,
            true
        )
        sendMelding(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.ID.str to UUID.randomUUID(),
                    Key.UUID.str to "uuid",
                    DataFelt.INNTEKTSMELDING_DOKUMENT.str to request
                )
            )
        )

        assertEquals(EventName.INNTEKTSMELDING_MOTTATT.name, rapid.inspektør.message(0).path(Key.EVENT_NAME.str).asText())
        assertEquals(BehovType.JOURNALFOER.name, rapid.inspektør.message(0).path(Key.BEHOV.str).asText())
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun sendMelding(melding: JsonMessage) {
        rapid.reset()
        rapid.sendTestMessage(melding.toJson())
    }
}
