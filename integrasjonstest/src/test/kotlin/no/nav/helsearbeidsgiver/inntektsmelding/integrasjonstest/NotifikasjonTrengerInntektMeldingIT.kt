package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.module.kotlin.contains
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class NotifikasjonTrengerInntektMeldingIT : EndToEndTest() {

    private val FNR = "fnr-123"
    private val ORGNR = "orgnr-456"
    private val FORESPOERSEL = UUID.randomUUID().toString()
    private val SAK_ID = "sak_id_123"
    private val OPPGAVE_ID = "oppgave_id_456"
    private val FORNAVN = "Ola"
    private val ETTERNAVN = "Normann"
    private val MELLOMNAVN = ""
    private val FØDSELSDATO: LocalDate = LocalDate.of(2012, 1, 15)

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
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
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
                //      Key.UUID.str to TRANSAKSJONS_ID,
                Key.ORGNRUNDERENHET.str to ORGNR,
                Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(10000)

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
        with(filter(EventName.SAK_OPPRETTET).first()) {
            assertEquals(SAK_ID, this[DataFelt.SAK_ID.str].asText())
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
                Key.ORGNRUNDERENHET.str to ORGNR,
                Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(8000)
        var transaksjonsId: String
        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.OPPRETT_OPPGAVE).first()) {
            assertNotNull(this[Key.UUID.str].asText().also { transaksjonsId = this[Key.UUID.str].asText() })
            assertEquals(ORGNR, this[Key.ORGNRUNDERENHET.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
        }

        with(filter(EventName.FORESPØRSEL_LAGRET, BehovType.PERSISTER_OPPGAVE_ID).first()) {
            assertEquals(OPPGAVE_ID, this[DataFelt.OPPGAVE_ID.str].asText())
            assertEquals(FORESPOERSEL, this[Key.FORESPOERSEL_ID.str].asText())
            assertEquals(transaksjonsId, this[Key.UUID.str].asText())
        }
        with(filter(EventName.OPPGAVE_LAGRET).first()) {
            assertEquals(OPPGAVE_ID, this[DataFelt.OPPGAVE_ID.str].asText())
            assertFalse(this.contains(Key.UUID.str))
        }
    }
}
