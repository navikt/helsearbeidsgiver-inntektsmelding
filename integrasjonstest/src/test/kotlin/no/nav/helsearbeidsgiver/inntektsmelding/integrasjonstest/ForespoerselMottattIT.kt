@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.pdl.PdlHentFullPerson
import no.nav.helsearbeidsgiver.pdl.PdlHentPersonNavn
import no.nav.helsearbeidsgiver.pdl.PdlPersonNavnMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ForespoerselMottattIT : EndToEndTest() {

    val FNR = "fnr-123"
    val ORGNR = "orgnr-456"
    val FORESPOERSEL = UUID.randomUUID().toString()
    val SAK_ID = "sak_id_123"
    val OPPGAVE_ID = "oppgave_id_456"
    val FORNAVN = "Ola"
    val ETTERNAVN = "Normann"
    val MELLOMNAVN = ""
    val FØDSELSDATO = LocalDate.of(2012, 1, 15)

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } answers {
            SAK_ID
        }
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
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
                Pri.Key.NOTIS.str to Pri.NotisType.FORESPØRSEL_MOTTATT.name,
                Pri.Key.ORGNR.str to ORGNR,
                Pri.Key.FNR.str to FNR,
                Pri.Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(8000)

        with(getMessage(0)) {
            assertEquals(BehovType.NOTIFIKASJON_TRENGER_IM.name, get(Key.BEHOV.str).asText())

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
        }

        with(getMessage(1)) {
            assertEquals(BehovType.FULLT_NAVN.name, get(Key.BEHOV.str)[0].asText())

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
        }

        with(getMessage(2)) {
            assertEquals(BehovType.FULLT_NAVN.name, get(Key.BEHOV.str)[0].asText())
            assertNotNull(get(Key.LØSNING.str).get(BehovType.FULLT_NAVN.name).asText())

            val løsning = get(Key.LØSNING.str).get(BehovType.FULLT_NAVN.name).toJsonElement().fromJson(NavnLøsning.serializer())
            assertNotNull(løsning)

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
        }

        with(getMessage(3)) {
            assertEquals(BehovType.PERSISTER_SAK_ID.name, get(Key.BEHOV.str)[0].asText())
            assertEquals(BehovType.OPPRETT_OPPGAVE.name, get(Key.BEHOV.str)[1].asText())

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
            assertEquals(SAK_ID, get(Key.SAK_ID.str).asText())
        }

        with(getMessage(4)) {
            assertEquals(BehovType.PERSISTER_OPPGAVE_ID.name, get(Key.BEHOV.str)[0].asText())

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
            assertEquals(SAK_ID, get(Key.SAK_ID.str).asText())
            assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
        }
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
