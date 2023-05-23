@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class AkkumulatorTest {

    private val rapid = TestRapid()
    private val redisStore = mockk<RedisStore>()
    private var akkumulator: Akkumulator
    private val timeout = 600L

    private val BEHOV_FULLT_NAVN = BehovType.FULLT_NAVN.toString()
    private val BEHOV_BRREG = BehovType.VIRKSOMHET.toString()
    private val BEHOV_ARBEIDSGIVERE = BehovType.ARBEIDSGIVERE.toString()

    private val UUID_BRREG = "uuid_" + BEHOV_BRREG
    private val UUID_PDL = "uuid_" + BEHOV_FULLT_NAVN

    val LØSNING_FEIL = NavnLøsning(error = Feilmelding("Fikk 500"))
    val LØSNING_OK = NavnLøsning(value = PersonDato("abc", LocalDate.now()))
    val PDL_OK = NavnLøsning(value = PersonDato("xyz", LocalDate.now()))

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        akkumulator = Akkumulator(rapid, redisStore, timeout)
    }

    @Test
    fun `skal publisere neste behov`() {
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN),
            "neste_behov" to listOf(BehovType.ARBEIDSGIVERE.toString()),
            "@løsning" to mapOf(
                BEHOV_FULLT_NAVN to PDL_OK
            ),
            "inntektsmelding" to "placeholder"
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(PDL_OK), timeout)
        }
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }

        val publisert = rapid.firstMessage().fromJsonMapOnlyKeys()

        // Skal beholde eksisterende verdier
        listOf(
            Key.INNTEKTSMELDING to "placeholder",
            Key.UUID to "uuid"
        )
            .forEach { (key, expectedValue) ->
                val actual = publisert[key]!!.fromJson(String.serializer())
                assertEquals(expectedValue, actual)
            }

        // Skal legge til neste behov i behovliste
        val behov = publisert[Key.BEHOV]!!.fromJson(String.serializer().list())
        assertEquals(BEHOV_FULLT_NAVN, behov[0])
        assertEquals(BEHOV_ARBEIDSGIVERE, behov[1])

        // Skal fjernes
        assertFalse(publisert.keys.contains(Key.LØSNING))
        assertEquals(0, publisert[Key.NESTE_BEHOV]!!.jsonArray.size)
    }

    @Test
    fun `skal lagre verdi`() {
        every { redisStore.get(UUID_BRREG) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_FULLT_NAVN to PDL_OK
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(PDL_OK), timeout)
        }
        verify(exactly = 0) {
            redisStore.set(UUID_BRREG, objectMapper.writeValueAsString(LØSNING_OK), timeout)
        }
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }
    }

    @Test
    fun `skal håndtere en feil før alle løsninger er klare`() {
        every { redisStore.get(UUID_BRREG) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_FULLT_NAVN to LØSNING_FEIL
            )
        )
        val løsningResultat = mapOf(
            BEHOV_FULLT_NAVN to LØSNING_FEIL
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(LØSNING_FEIL), any())
        }
        verify(exactly = 1) {
            redisStore.set("uuid", objectMapper.writeValueAsString(løsningResultat), any())
        }
    }

    @Test
    fun `skal behandle en feil blant løsninger`() {
        every { redisStore.set(UUID_PDL, any(), timeout) } returns Unit
        every { redisStore.get(UUID_BRREG) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN, BEHOV_BRREG),
            "@løsning" to mapOf( // PDL feiler først
                BEHOV_FULLT_NAVN to LØSNING_FEIL
            )
        )
        val resultat = mapOf(
            BEHOV_FULLT_NAVN to LØSNING_FEIL
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(LØSNING_FEIL), timeout)
        }
        verify(exactly = 1) {
            redisStore.set("uuid", objectMapper.writeValueAsString(resultat), any())
        }
    }

    @Test
    fun `skal behandle komplett løsning`() {
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_FULLT_NAVN to LØSNING_OK,
                BEHOV_BRREG to LØSNING_OK
            )
        )
        val løsningResultat = mapOf(
            BEHOV_FULLT_NAVN to LØSNING_OK,
            BEHOV_BRREG to LØSNING_OK
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set("uuid", objectMapper.writeValueAsString(løsningResultat), timeout)
        }
    }

    @Test
    fun `skal behandle ukomplett løsning`() {
        every { redisStore.get(UUID_BRREG) } returns objectMapper.writeValueAsString(LØSNING_OK)
        every { redisStore.get(UUID_PDL) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_FULLT_NAVN, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_BRREG to ""
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_BRREG, any(), timeout)
        }
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }
    }

    @Test
    fun `skal videresende boomerang i neste behov`() {
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val initId = "whateva"
        val dato = "whenever"
        val foedselsnummer = "fnr"
        val originalBoomerang = mapOf(
            Key.NESTE_BEHOV.str to listOf(BehovType.ARBEIDSGIVERE.toString()),
            Key.INITIATE_ID.str to initId,
            Key.INNTEKT_DATO.str to dato,
            Key.FNR.str to foedselsnummer
        )
        val melding = mapOf(
            Key.LØSNING.str to mapOf(
                BEHOV_FULLT_NAVN to LØSNING_OK
            ),
            Key.BEHOV.str to listOf(BEHOV_FULLT_NAVN),
            Key.BOOMERANG.str to originalBoomerang
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(any(), objectMapper.writeValueAsString(LØSNING_OK), timeout)
        }
        verify(exactly = 0) {
            redisStore.set(initId, any(), any())
        }
        val boomerang = rapid.firstMessage()
            .fromJsonMapOnlyKeys()[Key.BOOMERANG]!!
            .fromJsonMapOnlyKeys()

        // akkumulator skal fjerne neste behov!
        assertEquals(
            emptyList<BehovType>(),
            boomerang[Key.NESTE_BEHOV]?.fromJson(BehovType.serializer().list())
        )
        assertEquals(
            originalBoomerang.keys.toList(),
            boomerang.keys.map(Key::str), // må mappe om til string! :/
            "Alle keys skal beholdes i boomerang"
        )
    }
}
