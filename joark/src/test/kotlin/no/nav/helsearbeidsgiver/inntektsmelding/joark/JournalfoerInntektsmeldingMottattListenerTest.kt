package no.nav.helsearbeidsgiver.inntektsmelding.joark

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
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to request.let(Json::encodeToJsonElement)
        )
        assertEquals(EventName.INNTEKTSMELDING_MOTTATT.name, rapid.inspektør.message(0).path(Key.EVENT_NAME.str).asText())
        assertEquals(BehovType.JOURNALFOER.name, rapid.inspektør.message(0).path(Key.BEHOV.str).asText())
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }
}
