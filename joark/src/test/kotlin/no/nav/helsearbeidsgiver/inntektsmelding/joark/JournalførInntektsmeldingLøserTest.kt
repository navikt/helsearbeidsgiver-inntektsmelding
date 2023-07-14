package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyDatafelter
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.mockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JournalførInntektsmeldingLøserTest {

    private val testRapid = TestRapid()
    private val mockDokArkivClient = mockk<DokArkivClient>()

    init {
        JournalførInntektsmeldingLøser(testRapid, mockDokArkivClient)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal håndtere at dokarkiv feiler`() {
        coEvery {
            mockDokArkivClient.opprettJournalpost(any(), any(), any())
        } throws DokArkivException(Exception(""))

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmeldingDokument().let(Jackson::toJson),
            Key.UUID to "uuid-557".toJson()
        )

        val publisert = testRapid.firstMessage().let {
            it.fromJsonMapOnlyKeys() + it.fromJsonMapOnlyDatafelter()
        }

        publisert shouldContainKey Key.FAIL
        publisert shouldNotContainKey DataFelt.INNTEKTSMELDING_DOKUMENT

        val fail = publisert[Key.FAIL]
            .shouldNotBeNull()
            .toJsonNode()
            .let(Jackson::readFail)

        assertEquals("Kall mot dokarkiv feilet", fail.feilmelding)
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        coEvery {
            mockDokArkivClient.opprettJournalpost(any(), any(), any())
        } returns OpprettJournalpostResponse(
            journalpostId = "jid-ulende-koala",
            journalpostFerdigstilt = true,
            journalStatus = "FERDIGSTILT",
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmeldingDokument().let(Jackson::toJson),
            Key.UUID to "uuid-979".toJson()
        )

        val publisert = testRapid.firstMessage()
            .fromJsonMapOnlyKeys()
            .mapValues { (_, value) -> value.fromJson(String.serializer()) }

        assertEquals(BehovType.LAGRE_JOURNALPOST_ID.name, publisert[Key.BEHOV])
        assertEquals("jid-ulende-koala", publisert[Key.JOURNALPOST_ID])
        assertEquals("uuid-979", publisert[Key.UUID])
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.name.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to "xyz".toJson(),
            Key.UUID to "uuid-549".toJson()
        )

        val fail = testRapid.firstMessage()
            .fromJsonMapOnlyKeys()[Key.FAIL]
            .shouldNotBeNull()
            .toJsonNode()
            .let(Jackson::readFail)

        assertTrue(fail.feilmelding.isNotEmpty())
    }
}

private object Jackson {
    private val objectMapper = customObjectMapper()

    fun toJson(inntektsmelding: InntektsmeldingDokument): JsonElement =
        objectMapper.writeValueAsString(inntektsmelding).parseJson()

    fun readFail(json: JsonNode): Fail =
        objectMapper.treeToValue(json, Fail::class.java)
}
