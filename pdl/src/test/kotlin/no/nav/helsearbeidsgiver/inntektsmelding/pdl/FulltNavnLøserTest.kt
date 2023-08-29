package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
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
        CollectorRegistry.defaultRegistry.clear()
    }

    @Test
    fun `skal finne navn`() {
        coEvery {
            mockPdlClient.fullPerson(any())
        } returns mockPerson("Ola", "", "Normann", LocalDate.now())

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[DataFelt.ARBEIDSTAKER_INFORMASJON]
            .shouldNotBeNull()
            .fromJson(PersonDato.serializer())
            .navn
            .shouldBe("Ola Normann")

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

        publisert[DataFelt.ARBEIDSTAKER_INFORMASJON].shouldBeNull()

        publisert[Key.FAIL].shouldNotBeNull()
    }
}

private fun mockPerson(fornavn: String, mellomNavn: String, etternavn: String, fødselsdato: LocalDate): FullPerson =
    FullPerson(
        navn = PersonNavn(fornavn, mellomNavn, etternavn),
        foedselsdato = fødselsdato
    )
