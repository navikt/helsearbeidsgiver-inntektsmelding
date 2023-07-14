package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.OppgaveFerdigLøsning
import no.nav.helsearbeidsgiver.felles.SakFerdigLøsning
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyDatafelter
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockInnsendingRequest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.Jackson
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.lesLoesning
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingIT : EndToEndTest() {

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.innsendingRequest.orgnrUnderenhet)
        forespoerselRepository.oppdaterSakId(Mock.SAK_ID, Mock.forespoerselId.toString())
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        coEvery { arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any()) } returns Mock.SAK_ID

        coEvery { arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Mock.OPPGAVE_ID

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = Mock.JOURNALPOST_ID,
            journalpostFerdigstilt = true,
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.OPPRETTET to LocalDateTime.now().toJson(),
            Key.CLIENT_ID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            DataFelt.ORGNRUNDERENHET to Mock.innsendingRequest.orgnrUnderenhet.toJson(),
            Key.IDENTITETSNUMMER to Mock.innsendingRequest.identitetsnummer.toJson(),
            DataFelt.INNTEKTSMELDING to Mock.innsendingRequest.let(Jackson::toJson)
        )

        Thread.sleep(10000)

        messages.filter(EventName.INSENDING_STARTED)
            .filter(DataFelt.INNTEKTSMELDING_DOKUMENT)
            .first()
            .fromJsonMapOnlyDatafelter()
            .also {
                // Ble lagret i databasen
                it[DataFelt.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.JOURNALFOER, loesningPaakrevd = false)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_JOURNALPOST_ID, loesningPaakrevd = false)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                // EVENT: Mottatt inntektsmelding
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .first()
            .also {
                // EVENT: Journalføring
                val oppgaveId = it.fromJsonMapOnlyDatafelter()
                    .get(DataFelt.OPPGAVE_ID)
                    ?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID

                val msgKeyValues = it.fromJsonMapOnlyKeys()

                msgKeyValues[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
                msgKeyValues[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .filter(BehovType.DISTRIBUER_IM, loesningPaakrevd = false)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                // Be om å distribuere
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .filter(BehovType.ENDRE_SAK_STATUS, loesningPaakrevd = true)
            .first()
            .also {
                // Endre status for arbeidsgivernotifikasjon sak
                val sakId = it.fromJsonMapOnlyDatafelter()[DataFelt.SAK_ID]?.fromJsonToString()

                sakId shouldBe Mock.SAK_ID

                val loesning = it.fromJsonMapOnlyKeys()
                    .lesLoesning(BehovType.ENDRE_SAK_STATUS, SakFerdigLøsning.serializer())

                loesning?.value shouldBe Mock.SAK_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .filter(BehovType.ENDRE_OPPGAVE_STATUS, loesningPaakrevd = true)
            .first()
            .also {
                // Endre status for arbeidsgivernotifikasjon oppgave
                val oppgaveId = it.fromJsonMapOnlyDatafelter()[DataFelt.OPPGAVE_ID]?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID

                val loesning = it.fromJsonMapOnlyKeys()
                    .lesLoesning(BehovType.ENDRE_OPPGAVE_STATUS, OppgaveFerdigLøsning.serializer())

                loesning?.value shouldBe Mock.OPPGAVE_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .first()
            .also { msg ->
                // Verifiser at inntektsmelding er distribuert på ekstern kafka
                val journalpostId = msg.fromJsonMapOnlyKeys()[Key.JOURNALPOST_ID]?.fromJsonToString()

                journalpostId shouldBe Mock.JOURNALPOST_ID

                msg.fromJsonMapOnlyDatafelter()[DataFelt.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }
    }

    private object Mock {
        const val SAK_ID = "sak-id-skoleboller"
        const val OPPGAVE_ID = "oppgave-id-skoleboller"
        const val JOURNALPOST_ID = "jorunalpost-id-skoleboller"

        val forespoerselId = UUID.randomUUID()
        val innsendingRequest = mockInnsendingRequest()
    }
}
