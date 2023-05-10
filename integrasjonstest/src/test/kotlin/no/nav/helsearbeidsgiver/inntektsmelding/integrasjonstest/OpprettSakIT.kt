package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.module.kotlin.contains
import io.mockk.coEvery
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
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class OpprettSakIT : EndToEndTest() {

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

    @Test
    fun `Oppretter og lagrer sak etter at forespørselen er mottatt`() {
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } answers {
            SAK_ID
        }
        /*
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
        }*/

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
        /*
                with(filter(EventName.FORESPØRSEL_MOTTATT, BehovType.FULLT_NAVN).first()) {
                    assertEquals(BehovType.FULLT_NAVN.name, get(Key.BEHOV.str)[0].asText())

                    assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
                    assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
                    assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
                    assertEquals(FORESPOERSEL, get(Key.FORESPOERSEL_ID.str).asText())
                }

                with(filter(EventName.FORESPØRSEL_MOTTATT, BehovType.FULLT_NAVN, løsning = true).first()) {
                    assertEquals(BehovType.FULLT_NAVN.name, get(Key.BEHOV.str)[0].asText())
                    assertNotNull(get(Key.LØSNING.str).get(BehovType.FULLT_NAVN.name).asText())

                    val løsning = get(Key.LØSNING.str).get(BehovType.FULLT_NAVN.name).toJsonElement().fromJson(NavnLøsning.serializer())
                    assertNotNull(løsning)

                    assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
                    assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
                    assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
                    assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
                }

                with(filter(EventName.FORESPØRSEL_MOTTATT, BehovType.PERSISTER_SAK_ID).first()) {
                    assertEquals(BehovType.PERSISTER_SAK_ID.name, get(Key.BEHOV.str)[0].asText())
                    assertEquals(BehovType.OPPRETT_OPPGAVE.name, get(Key.BEHOV.str)[1].asText())

                    assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
                    assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
                    assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
                    assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
                    assertEquals(SAK_ID, get(Key.SAK_ID.str).asText())
                }

                with(filter(EventName.FORESPØRSEL_MOTTATT, BehovType.PERSISTER_OPPGAVE_ID).first()) {
                    assertEquals(BehovType.PERSISTER_OPPGAVE_ID.name, get(Key.BEHOV.str)[0].asText())

                    assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
                    assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
                    assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
                    assertEquals(FORESPOERSEL, get(Key.UUID.str).asText())
                    assertEquals(SAK_ID, get(Key.SAK_ID.str).asText())
                    assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
                }

         */
    }
}
