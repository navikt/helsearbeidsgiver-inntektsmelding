package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.OppgaveFerdigLøsning
import no.nav.helsearbeidsgiver.felles.SakFerdigLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Innsending av skjema fra frontend")
class InnsendingIT : EndToEndTest() {

    private val FNR = "fnr-123"
    private val ORGNR = "orgnr-456"
    private val SAK_ID = "sak_id_123"
    private val OPPGAVE_ID = "oppgave_id_456"
    private val FORESPØRSEL_ID = UUID.randomUUID().toString()
    private val CLIENT_ID = UUID.randomUUID().toString()
    private val REQUEST = mockRequest()
    private val JOURNALPOST_ID = "jp-789"

    private fun setup() {
        forespoerselRepository.lagreForespørsel(FORESPØRSEL_ID, ORGNR)
        forespoerselRepository.oppdaterSakId(SAK_ID, FORESPØRSEL_ID)
        forespoerselRepository.oppdaterOppgaveId(FORESPØRSEL_ID, OPPGAVE_ID)

        // Mocking
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } answers {
            SAK_ID
        }
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            OPPGAVE_ID
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
                Key.CLIENT_ID to CLIENT_ID,
                Key.FORESPOERSEL_ID.str to FORESPØRSEL_ID,
                DataFelt.ORGNRUNDERENHET.str to REQUEST.orgnrUnderenhet,
                Key.IDENTITETSNUMMER.str to REQUEST.identitetsnummer,
                DataFelt.INNTEKTSMELDING.str to REQUEST
            )
        )
        Thread.sleep(10000)

        assertNotNull(meldinger)

        with(filter(EventName.INSENDING_STARTED, datafelt = DataFelt.INNTEKTSMELDING_DOKUMENT).first()) {
            // Ble lagret i databasen
            assertNotNull(get(DataFelt.INNTEKTSMELDING_DOKUMENT.str))
        }

        with(filter(EventName.INNTEKTSMELDING_MOTTATT, BehovType.JOURNALFOER, løsning = false).first()) {
            // Journalført i dokarkiv
            assertEquals(FORESPØRSEL_ID, get(Key.FORESPOERSEL_ID.str).asText())
        }
        with(filter(EventName.INNTEKTSMELDING_MOTTATT, BehovType.LAGRE_JOURNALPOST_ID, løsning = false).first()) {
            // Journalført i dokarkiv
            assertEquals(FORESPØRSEL_ID, get(Key.FORESPOERSEL_ID.str).asText())
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_MOTTATT, null, løsning = false).first()) {
            // EVENT: Mottatt inntektsmelding
            assertEquals(FORESPØRSEL_ID, get(Key.FORESPOERSEL_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, null, løsning = false).first()) {
            // EVENT: Journalføring
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertEquals(FORESPØRSEL_ID, get(Key.FORESPOERSEL_ID.str).asText())
            assertEquals(OPPGAVE_ID, get(DataFelt.OPPGAVE_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.DISTRIBUER_IM, løsning = false).first()) {
            // Be om å distribuere
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.ENDRE_SAK_STATUS, løsning = true).first()) {
            // Endre status for arbeidsgivernotifikasjon sak
            assertEquals(SAK_ID, get(DataFelt.SAK_ID.str).asText())
            val løsning: SakFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_SAK_STATUS.name).toJsonElement().fromJson(SakFerdigLøsning.serializer())
            assertEquals(SAK_ID, løsning.value)
        }

        with(filter(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.ENDRE_OPPGAVE_STATUS, løsning = true).first()) {
            // Endre status for arbeidsgivernotifikasjon oppgave
            assertEquals(OPPGAVE_ID, get(DataFelt.OPPGAVE_ID.str).asText())
            val løsning: OppgaveFerdigLøsning =
                get(Key.LØSNING.str).get(BehovType.ENDRE_OPPGAVE_STATUS.name).toJsonElement().fromJson(OppgaveFerdigLøsning.serializer())
            assertEquals(OPPGAVE_ID, løsning.value)
        }

        with(filter(EventName.INNTEKTSMELDING_DISTRIBUERT).first()) {
            // Verifiser at inntektsmelding er distribuert på ekstern kafka
            assertEquals(JOURNALPOST_ID, get(Key.JOURNALPOST_ID.str).asText())
            assertNotNull(get(DataFelt.INNTEKTSMELDING_DOKUMENT.str))
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
                Naturalytelse(
                    NaturalytelseKode.KOSTDOEGN,
                    LocalDate.now(),
                    300.0.toBigDecimal()
                )
            ),
            ÅrsakInnsending.ENDRING,
            true
        )
    }
}
