package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.nyStatusSakByGrupperingsid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.oppgaveUtfoert
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.LagreJournalpostLøsning
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
        repository.lagreForespørsel(FORESPØRSEL_ID)
        repository.oppdaterSakId(SAK_ID, FORESPØRSEL_ID)
        repository.oppdaterOppgaveId(FORESPØRSEL_ID, OPPGAVE_ID)

        // Mocking
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(any(), any(), any())
        } answers {
            "no-?"
        }

        coEvery {
            arbeidsgiverNotifikasjonKlient.oppgaveUtfoert(any())
        } answers {
            "no-?"
        }

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

        with(getMessage(7)) {
            // Inntektsmelding be lagret i databasen
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
            assertEquals(EventName.INSENDING_STARTED.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(BehovType.PERSISTER_IM.name, get(Key.BEHOV.str)[0].asText())
            assertNotNull(get(Key.LØSNING.str).asText())
            assertNotNull(get(Key.INNTEKTSMELDING.str).asText())
            val løsning: PersisterImLøsning = get(Key.LØSNING.str).get(BehovType.PERSISTER_IM.name).toJsonElement().fromJson(PersisterImLøsning.serializer())
            assertNull(løsning.error)
        }

        with(getMessage(14)) {
            // Journalføring var velykket
            assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
            assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
        }

        with(getMessage(13)) {
            // Lagret journalpostId i databasen
            assertEquals(EventName.INNTEKTSMELDING_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(FORESPØRSEL_ID, get(Key.UUID.str).asText())
            assertEquals(BehovType.LAGRE_JOURNALPOST_ID.name, get(Key.BEHOV.str).asText())
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertNotNull(get(Key.LØSNING.str).asText())
            val løsning: LagreJournalpostLøsning =
                get(Key.LØSNING.str).get(BehovType.LAGRE_JOURNALPOST_ID.name).toJsonElement().fromJson(LagreJournalpostLøsning.serializer())
            assertNull(løsning.error)
            assertEquals(JOURNALPOST_ID, løsning.value)
            // assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
        }

        with(getMessage(15)) {
            // Be om å endre status for sak/oppgave og distribuere
            assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertEquals(OPPGAVE_ID, get(Key.OPPGAVE_ID.str).asText())
            assertEquals(BehovType.DISTRIBUER_IM.name, get(Key.BEHOV.str)[0].asText())
            assertEquals(BehovType.ENDRE_SAK_STATUS.name, get(Key.BEHOV.str)[1].asText())
            assertEquals(BehovType.ENDRE_OPPGAVE_STATUS.name, get(Key.BEHOV.str)[2].asText())
        }

        with(getMessage(15)) {
            // Distribuer
            assertEquals(BehovType.DISTRIBUER_IM.name, get(Key.BEHOV.str)[0].asText())
            // assertNotNull(get(Key.LØSNING.str).asText())
        }

        with(getMessage(16)) {
            // SakFerdigLøser - endre status
            assertEquals(BehovType.ENDRE_SAK_STATUS.name, get(Key.BEHOV.str)[1].asText())
            assertNotNull(get(Key.LØSNING.str).asText())
            val løsning: SakFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_SAK_STATUS.name).toJsonElement().fromJson(SakFerdigLøsning.serializer())
            assertEquals(FORESPØRSEL_ID, løsning.value)
            assertNull(løsning.error)
        }

        with(getMessage(17)) {
            // OppgaveFerdigLøser - endre status
            assertEquals(BehovType.ENDRE_OPPGAVE_STATUS.name, get(Key.BEHOV.str)[2].asText())
            assertNotNull(get(Key.LØSNING.str).asText())
            val løsning: OppgaveFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_OPPGAVE_STATUS.name).toJsonElement().fromJson(OppgaveFerdigLøsning.serializer())
            assertEquals(OPPGAVE_ID, løsning.value)
            assertNull(løsning.error)
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
