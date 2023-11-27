package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class JournalfoerInntektsmeldingMottattListenerTest {
    private val rapid = TestRapid()
    private var inntektMottat: JournalfoerInntektsmeldingMottattListener

    init {
        inntektMottat = JournalfoerInntektsmeldingMottattListener(rapid)
    }

    @Test
    fun publisererEventOgBehovVedMottattInntektsmelding() {
        val request = Innsending(
            "",
            "",
            emptyList(),
            emptyList(),
            emptyList(),
            LocalDate.now(),
            emptyList(),
            Inntekt(
                bekreftet = true,
                500.0,
                Bonus(),
                true
            ),
            FullLoennIArbeidsgiverPerioden(
                true
            ),
            Refusjon(
                true
            ),
            emptyList(),
            no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending.NY,
            true
        )
        sendMelding(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to UUID.randomUUID(),
                    Key.INNTEKTSMELDING_DOKUMENT.str to request
                )
            )
        )

        assertEquals(EventName.INNTEKTSMELDING_MOTTATT.name, rapid.inspektør.message(0).path(Key.EVENT_NAME.str).asText())
        assertEquals(BehovType.JOURNALFOER.name, rapid.inspektør.message(0).path(Key.BEHOV.str).asText())
    }

    private fun sendMelding(melding: JsonMessage) {
        rapid.reset()
        rapid.sendTestMessage(melding.toJson())
    }
}
