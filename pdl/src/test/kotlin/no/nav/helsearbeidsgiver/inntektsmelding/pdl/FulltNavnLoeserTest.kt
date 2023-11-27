package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juni
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FulltNavnLoeserTest {

    private val testRapid = TestRapid()
    private val mockPdlClient = mockk<PdlClient>()

    init {
        FulltNavnLoeser(testRapid, mockPdlClient)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        CollectorRegistry.defaultRegistry.clear()
    }

    @Test
    fun `skal finne navn`() {
        val id = "123"
        coEvery { mockPdlClient.personBolk(any()) } returns listOf(mockPerson("Ola", id))

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to id.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.ARBEIDSTAKER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("Ola Normann")
        publisert[Key.ARBEIDSGIVER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .ident
            .shouldBe("")
        publisert[Key.FAIL].shouldBeNull()
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.ARBEIDSTAKER_INFORMASJON].shouldBeNull()
        publisert[Key.ARBEIDSGIVER_INFORMASJON].shouldBeNull()

        publisert[Key.FAIL].shouldNotBeNull()
    }

    @Test
    fun `skal returnere navn på både arbeidstaker og arbeidsgiver`() {
        val arbeidstakerID = "123456"
        val arbeidsgiverID = "654321"
        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockPerson("Ola", arbeidstakerID),
            mockPerson("Kari", arbeidsgiverID)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to arbeidstakerID.toJson(),
            Key.ARBEIDSGIVER_ID to arbeidsgiverID.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.ARBEIDSTAKER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("Ola Normann")
        publisert[Key.ARBEIDSGIVER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("Kari Normann")
        publisert[Key.FAIL].shouldBeNull()
    }

    @Test
    fun `skal returnere navn på arbeidsgiver og tomt navn på arbeidstaker dersom arbeidstaker ikke blir funnet`() {
        val arbeidstakerID = "123456"
        val arbeidsgiverID = "654321"
        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockPerson("Kari", arbeidsgiverID)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to arbeidstakerID.toJson(),
            Key.ARBEIDSGIVER_ID to arbeidsgiverID.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.ARBEIDSTAKER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("")
        publisert[Key.ARBEIDSGIVER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("Kari Normann")
        publisert[Key.FAIL].shouldBeNull()
    }
}

private fun mockPerson(fornavn: String, ident: String): FullPerson =
    FullPerson(
        navn = PersonNavn(
            fornavn = fornavn,
            mellomnavn = null,
            etternavn = "Normann"
        ),
        foedselsdato = 13.juni(1956),
        ident = ident
    )
