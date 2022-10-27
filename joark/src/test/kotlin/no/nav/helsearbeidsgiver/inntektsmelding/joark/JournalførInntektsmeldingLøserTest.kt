@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class JournalførInntektsmeldingLøserTest {

    private val rapid = TestRapid()
    private var løser: JournalførInntektsmeldingLøser
    private val BEHOV = BehovType.JOURNALFOER.name
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        løser = JournalførInntektsmeldingLøser(rapid)
    }

    fun sendMessage(mapOf: Map<String, Any>): JournalpostLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<JournalpostLøsning>(losning.get(BEHOV).toString())
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "inntektsmelding" to mapOf(
                    "identitetsnummer" to "123",
                    "orgnrUnderenhet" to "abc",
                    "behandlingsdagerFom" to "2022-10-01",
                    "behandlingsdagerTom" to "2022-10-11",
                    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
                    "egenmeldinger" to listOf(
                        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
                        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
                    ),
                    "bruttonInntekt" to "25300",
                    "bruttoBekreftet" to "true",
                    "utbetalerFull" to "true",
                    "begrunnelseRedusert" to "BeskjedGittForSent",
                    "utbetalerHeleEllerDeler" to "false",
                    "refusjonPrMnd" to "19500",
                    "opphørerKravet" to "false",
                    "opphørSisteDag" to "2022-08-08",
                    "naturalytelser" to listOf(
                        mapOf(
                            "naturalytelseKode" to "abc",
                            "dato" to "2022-08-08",
                            "beløp" to "123"
                        )
                    ),
                    "bekreftOpplysninger" to "true"
                )
            )
        )
        assertEquals("jp-123", løsning.value)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "000",
                "orgnrUnderenhet" to "abc",
                "inntektsmelding" to "xyz"
            )
        )
        assertNotNull(løsning.error)
    }
}
