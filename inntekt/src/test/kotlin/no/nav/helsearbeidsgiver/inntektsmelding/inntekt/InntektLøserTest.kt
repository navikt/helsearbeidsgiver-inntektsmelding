package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.inntekt.Ident
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InntektLøserTest {

    private val rapid = TestRapid()
    private var inntektLøser: InntektLøser
    private var inntektKlient: InntektKlient

    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val BEHOV_BRREG = BehovType.VIRKSOMHET.toString()
    private val inntekt = Inntekt(200.0, historisk = emptyList())

    private val LØSNING_FEIL = InntektLøsning(error = Feilmelding("Fikk 500"))
    private val LØSNING_OK = InntektLøsning(value = inntekt)

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        inntektKlient = mockk<InntektKlient>()
        inntektLøser = InntektLøser(rapid, inntektKlient)
    }

    @Test
    fun `skal publisere svar når man får hentet inntekt`() {
        val response = InntektskomponentResponse(
            emptyList(),
            Ident("abc", "")
        )
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } returns response
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_PDL to inntekt
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
    }
}
