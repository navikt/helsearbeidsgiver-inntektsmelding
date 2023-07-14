package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.PdlHentFullPerson
import no.nav.helsearbeidsgiver.pdl.PdlPersonNavnMetadata
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class FulltNavnLøserTest {

    private val testRapid = TestRapid()
    private val mockPdlClient = mockk<PdlClient>()

    init {
        FulltNavnLøser(testRapid, mockPdlClient)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal finne navn`() {
        coEvery {
            mockPdlClient.fullPerson(any(), any())
        } returns mockPerson("Ola", "", "Normann", LocalDate.now())

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to listOf(BehovType.FULLT_NAVN).toJson(BehovType.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson()
        )

        val loesning = testRapid.firstMessage().lesLoesning()

        assertNotNull(loesning.value)
        assertEquals("Ola Normann", loesning.value!!.navn)
        assertNull(loesning.error)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to listOf(BehovType.FULLT_NAVN).toJson(BehovType.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson()
        )

        val loesning = testRapid.firstMessage().lesLoesning()

        assertNull(loesning.value)
        assertNotNull(loesning.error)
    }
}

private fun JsonElement.lesLoesning(): NavnLøsning =
    fromJsonMapOnlyKeys()
        .get(Key.LØSNING)
        .shouldNotBeNull()
        .fromJsonMapFiltered(BehovType.serializer())
        .get(BehovType.FULLT_NAVN)
        .shouldNotBeNull()
        .fromJson(NavnLøsning.serializer())

private fun mockPerson(fornavn: String, mellomNavn: String, etternavn: String, fødselsdato: LocalDate): PdlHentFullPerson =
    PdlHentFullPerson(
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
