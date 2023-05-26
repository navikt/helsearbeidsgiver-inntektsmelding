package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.PdlHentFullPerson
import no.nav.helsearbeidsgiver.pdl.PdlPersonNavnMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class FulltNavnLøserTest {

    private val rapid = TestRapid()
    private var løser: FulltNavnLøser
    private val BEHOV = BehovType.FULLT_NAVN
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private val pdlClient = mockk<PdlClient>()

    init {
        løser = FulltNavnLøser(rapid, pdlClient)
    }

    @Test
    fun `skal finne navn`() {
        coEvery {
            pdlClient.fullPerson(any(), any())
        } returns mockPerson("Ola", "", "Normann", LocalDate.now())
        val løsning = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED,
                Key.BEHOV.str to listOf(BEHOV.name),
                Key.ID.str to UUID.randomUUID(),
                Key.IDENTITETSNUMMER.str to "abc"
            )
        )
        assertNotNull(løsning.value)
        assertEquals("Ola Normann", løsning.value!!.navn)
        assertNull(løsning.error)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val løsning = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED,
                Key.BEHOV.str to listOf(BEHOV.name),
                Key.ID.str to UUID.randomUUID(),
                Key.IDENTITETSNUMMER.str to "abc"
            )
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    fun sendMessage(packet: Map<String, Any>): NavnLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path(Key.LØSNING.str)
        return objectMapper.readValue<NavnLøsning>(losning.get(BEHOV.name).toString())
    }

    fun mockPerson(fornavn: String, mellomNavn: String, etternavn: String, fødselsdato: LocalDate): PdlHentFullPerson {
        return PdlHentFullPerson(
            hentPerson = PdlHentFullPerson.PdlFullPersonliste(
                navn = listOf(PdlHentFullPerson.PdlFullPersonliste.PdlNavn(fornavn, mellomNavn, etternavn, PdlPersonNavnMetadata(""))),
                foedsel = listOf(PdlHentFullPerson.PdlFullPersonliste.PdlFoedsel(fødselsdato)),
                doedsfall = emptyList(),
                adressebeskyttelse = emptyList(),
                statsborgerskap = emptyList(),
                bostedsadresse = emptyList(),
                kjoenn = emptyList()
            ),
            hentIdenter = PdlHentFullPerson.PdlIdentResponse(
                emptyList()
            ),
            hentGeografiskTilknytning = PdlHentFullPerson.PdlGeografiskTilknytning(
                PdlHentFullPerson.PdlGeografiskTilknytning.PdlGtType.KOMMUNE,
                null,
                null,
                null
            )
        )
    }
}
