package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersisterImLøserTest {

    private val rapid = TestRapid()
    private var løser: PersisterImLøser
    private val repository = mockk<Repository>()

    init {
        løser = PersisterImLøser(rapid, repository)
    }

    private fun sendMelding(melding: JsonMessage) {
        rapid.reset()
        rapid.sendTestMessage(melding.toJson())
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagre(any(), any())
        } returns "abc"

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
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.BEHOV.str to listOf(BehovType.PERSISTER_IM.name),
                    Key.ID.str to UUID.randomUUID(),
                    Key.UUID.str to "uuid",
                    Key.INNTEKTSMELDING.str to request
                )
            )
        )
        val message = rapid.inspektør.message(1)
        Assertions.assertEquals(EventName.INSENDING_STARTED.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertNotNull(message.path(Key.INNTEKTSMELDING.str).asText())
    }
}
