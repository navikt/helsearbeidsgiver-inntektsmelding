package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.InntektEndringÅrsak
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.Inntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
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
                500.0,
                InntektEndringÅrsak.Bonus,
                true
            ),
            FullLønnIArbeidsgiverPerioden(
                true
            ),
            Refusjon(
                true
            ),
            emptyList(),
            ÅrsakInnsending.Ny,
            true
        )

        sendMelding(
            Key.BEHOV to listOf(BehovType.PERSISTER_IM.name).toJson(String::toJson),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.INNTEKTSMELDING to request.let(Json::encodeToJsonElement)
        )
        val message = rapid.inspektør.message(1)
        Assertions.assertEquals(EventName.INNTEKTSMELDING_MOTTATT.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertNotNull(message.path(Key.INNTEKTSMELDING.str).asText())
    }
}
