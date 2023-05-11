package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockPerson
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.pdl.PdlHentPersonNavn
import no.nav.helsearbeidsgiver.pdl.PdlPersonNavnMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class NotifikasjonTrengerInntektMeldingIT : EndToEndTest() {

    val FNR = "fnr-123"
    val ORGNR = "orgnr-456"
    val FORESPOERSEL = UUID.randomUUID().toString()
    val SAK_ID = "sak_id_123"
    val OPPGAVE_ID = "oppgave_id_456"
    val FORNAVN = "Ola"
    val ETTERNAVN = "Normann"
    val MELLOMNAVN = ""
    val FØDSELSDATO = LocalDate.of(2012, 1, 15)
    val TRANSAKSJONS_ID = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
        resetMessages()
    }

    @Test
    fun `Oppretter og lagrer sak etter at forespørselen er mottatt`() {
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } answers {
            SAK_ID
        }

        val pdlClient = this.pdlClient
        coEvery {
            pdlClient.personNavn(any())
        } answers {
            PdlHentPersonNavn.PdlPersonNavneliste(
                listOf(PdlHentPersonNavn.PdlPersonNavneliste.PdlPersonNavn(FORNAVN, MELLOMNAVN, ETTERNAVN, PdlPersonNavnMetadata("")))
            )
        }
        coEvery {
            pdlClient.fullPerson(any())
        } answers {
            mockPerson(FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO)
        }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                Key.IDENTITETSNUMMER.str to FNR,
                Key.UUID.str to TRANSAKSJONS_ID,
                Key.ORGNRUNDERENHET.str to ORGNR,
                Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(8000)

        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.FULLT_NAVN).first()) {
            assertEquals(FNR, this[Key.IDENTITETSNUMMER.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }

        with(filter(EventName.FORESPØRSEL_LAGRET, datafelt = DataFelt.ARBEIDSTAKER_INFORMASJON).first()) {
            assertNotNull(customObjectMapper().treeToValue(this[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java))
        }

        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.OPPRETT_SAK).first()) {
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }
        with(filter(EventName.FORESPØRSEL_LAGRET, datafelt = DataFelt.SAK_ID).first()) {
            assertEquals(SAK_ID, this[DataFelt.SAK_ID.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }
    }

    @Test
    fun `Oppretter og lagrer oppgave etter at forespørselen er mottatt`() {
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
        }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                Key.IDENTITETSNUMMER.str to FNR,
                Key.UUID.str to TRANSAKSJONS_ID,
                Key.ORGNRUNDERENHET.str to ORGNR,
                Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(8000)

        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.FULLT_NAVN).first()) {
            assertEquals(FNR, this[Key.IDENTITETSNUMMER.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }

        with(filter(EventName.FORESPØRSEL_LAGRET, datafelt = DataFelt.ARBEIDSTAKER_INFORMASJON).first()) {
            assertNotNull(customObjectMapper().treeToValue(this[DataFelt.ARBEIDSTAKER_INFORMASJON.str], PersonDato::class.java))
        }

        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.OPPRETT_SAK).first()) {
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }
        with(filter(EventName.FORESPØRSEL_LAGRET, datafelt = DataFelt.SAK_ID).first()) {
            assertEquals(SAK_ID, this[DataFelt.SAK_ID.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }
    }
}
