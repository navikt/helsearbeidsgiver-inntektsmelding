package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.nyStatusSak
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.nyStatusSakByGrupperingsid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.oppgaveUtfoert
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.OppgaveFerdigLøsning
import no.nav.helsearbeidsgiver.felles.PersisterImLøsning
import no.nav.helsearbeidsgiver.felles.SakFerdigLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Innsending av skjema fra frontend")
internal class InnsendingIT : EndToEndTest() {

    val FNR = "fnr-123"
    val ORGNR = "orgnr-456"
    val SAK_ID = "sak_id_123"
    val OPPGAVE_ID = "oppgave_id_456"
    val FORESPØRSEL_ID = UUID.randomUUID().toString()
    val REQUEST = mockRequest()
    val JOURNALPOST_ID = "jp-789"

    fun setup() {
        repository.lagreForespørsel(FORESPØRSEL_ID, ORGNR)
        repository.oppdaterSakId(SAK_ID, FORESPØRSEL_ID)
        repository.oppdaterOppgaveId(FORESPØRSEL_ID, OPPGAVE_ID)

        // Mocking
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.nyStatusSak(any(), any(), any(), any())
        } answers {
            "?"
        }

        coEvery {
            arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any())
        } answers {
            "?"
        }

        coEvery {
            arbeidsgiverNotifikasjonKlient.oppgaveUtfoert(any())
        } answers {
            "?"
        }

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } answers {
            SAK_ID
        }
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
        }
        coEvery {
            aaregClient.hentArbeidsforhold(any(), any())
        } answers {
            emptyList()
        }
        coEvery {
            dokarkivClient.opprettJournalpost(any(), any(), any())
        } answers {
            OpprettJournalpostResponse(JOURNALPOST_ID, journalpostFerdigstilt = true, "FERDIGSTILT", "", emptyList())
        }
    }

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        setup()
        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.UUID.str to FORESPØRSEL_ID,
                Key.ORGNRUNDERENHET.str to REQUEST.orgnrUnderenhet,
                Key.IDENTITETSNUMMER.str to REQUEST.identitetsnummer,
                Key.INNTEKTSMELDING.str to REQUEST
            )
        )
        Thread.sleep(10000)

        assertNotNull(meldinger)

        with(filter(EventName.INSENDING_STARTED, BehovType.PERSISTER_IM, true).first()) {
            // Ble lagret i databasen
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
            assertNotNull(get(Key.INNTEKTSMELDING.str).asText())
            val løsning: PersisterImLøsning = get(Key.LØSNING.str).get(BehovType.PERSISTER_IM.name).toJsonElement().fromJson(PersisterImLøsning.serializer())
            assertNull(løsning.error)
        }

        with(filter(EventName.INNTEKTSMELDING_MOTTATT, BehovType.JOURNALFOER, true).first()) {
            // Journalført i dokarkiv
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_MOTTATT, null, false).first()) {
            // EVENT: Mottatt inntektsmelding
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, null, false).first()) {
            // EVENT: Journalføring
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
            assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.DISTRIBUER_IM, false).first()) {
            // Be om å distribuere
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.ENDRE_SAK_STATUS, true).first()) {
            // Endre status for arbeidsgivernotifikasjon sak
            assertEquals(SAK_ID, get(Key.SAK_ID.str).asText())
            val løsning: SakFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_SAK_STATUS.name).toJsonElement().fromJson(SakFerdigLøsning.serializer())
            assertEquals(SAK_ID, løsning.value)
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.ENDRE_OPPGAVE_STATUS, true).first()) {
            // Endre status for arbeidsgivernotifikasjon oppgave
            assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
            val løsning: OppgaveFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_OPPGAVE_STATUS.name).toJsonElement().fromJson(OppgaveFerdigLøsning.serializer())
            assertEquals(OPPGAVE_ID, løsning.value)
        }
    }

    private fun mockRequest(): InnsendingRequest {
        return InnsendingRequest(
            ORGNR,
            FNR,
            listOf(LocalDate.now().plusDays(5)),
            listOf(
                no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(
                    LocalDate.now(),
                    LocalDate.now().plusDays(2)
                )
            ),
            emptyList(),
            LocalDate.now(),
            emptyList(),
            Inntekt(true, 32100.0.toBigDecimal(), endringÅrsak = null, false),
            FullLonnIArbeidsgiverPerioden(
                true,
                BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT
            ),
            Refusjon(true, 200.0.toBigDecimal(), LocalDate.now()),
            listOf(
                no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse(
                    no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode.KOST_DOEGN,
                    LocalDate.now(),
                    300.0.toBigDecimal()
                )
            ),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.ENDRING,
            true
        )
    }
}
