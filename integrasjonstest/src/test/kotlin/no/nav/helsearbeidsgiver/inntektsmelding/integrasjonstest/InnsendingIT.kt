package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingIT : EndToEndTest() {

    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.innsending.orgnrUnderenhet)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
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
            DataFelt.ORGNRUNDERENHET to Mock.innsending.orgnrUnderenhet.toJson(),
            Key.IDENTITETSNUMMER to Mock.innsending.identitetsnummer.toJson(),
            Key.ARBEIDSGIVER_ID to Mock.innsending.identitetsnummer.toJson(),
            DataFelt.INNTEKTSMELDING to Mock.innsending.toJson(Innsending.serializer())
        )

        Thread.sleep(10000)

        messages.filter(EventName.INSENDING_STARTED)
            .filter(DataFelt.INNTEKTSMELDING_DOKUMENT)
            .first()
            .toMap()
            .also {
                // Ble lagret i databasen
                it[DataFelt.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }
        messages.filter(EventName.INSENDING_STARTED)
            .filter(DataFelt.ER_DUPLIKAT_IM)
            .first()
            .toMap()
            .also {
                it[DataFelt.ER_DUPLIKAT_IM]!!.fromJson(Boolean.serializer()) shouldBe false
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .first()
            .toMap()
            .also {
                // EVENT: Mottatt inntektsmelding
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.JOURNALFOER)
            .first()
            .toMap()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .first()
            .toMap()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .first()
            .toMap()
            .also {
                it shouldContainKey DataFelt.INNTEKTSMELDING_DOKUMENT
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .filter(BehovType.DISTRIBUER_IM)
            .first()
            .toMap()
            .also {
                // Be om å distribuere
                it shouldContainKey DataFelt.INNTEKTSMELDING_DOKUMENT
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .first()
            .toMap()
            .also {
                // Verifiser at inntektsmelding er distribuert på ekstern kafka
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID

                it[DataFelt.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }

        bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak()

        bekreftMarkeringAvForespoerselSomBesvart()
    }

    @Test
    fun `skal ikke lagre duplikat inntektsmelding`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.innsending.orgnrUnderenhet)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        imRepository.lagreInntektsmelding(Mock.forespoerselId.toString(), Mock.innsendtInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = Mock.JOURNALPOST_ID,
            journalpostFerdigstilt = true,
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        coEvery { brregClient.hentVirksomhetNavn(any()) } returns "Bedrift A/S"

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.OPPRETTET to LocalDateTime.now().toJson(),
            Key.CLIENT_ID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            DataFelt.ORGNRUNDERENHET to Mock.innsending.orgnrUnderenhet.toJson(),
            Key.IDENTITETSNUMMER to "fnr-bjarne".toJson(),
            Key.ARBEIDSGIVER_ID to "fnr-max".toJson(),
            DataFelt.INNTEKTSMELDING to Mock.innsending.toJson(Innsending.serializer())
        )

        Thread.sleep(10000)

        messages.filter(EventName.INSENDING_STARTED)
            .filter(DataFelt.ER_DUPLIKAT_IM)
            .first()
            .toMap()
            .also {
                it[DataFelt.ER_DUPLIKAT_IM]!!.fromJson(Boolean.serializer()) shouldBe true
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT).all() shouldHaveSize 0
    }

    private fun bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak() {
        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(BehovType.NOTIFIKASJON_HENT_ID)
            .first()
            .toMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(DataFelt.SAK_ID, utenDataKey = true)
            .filter(DataFelt.OPPGAVE_ID, utenDataKey = true)
            .first()
            .toMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                DataFelt.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
                DataFelt.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }

        messages.filter(EventName.SAK_FERDIGSTILT)
            .first()
            .toMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                DataFelt.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages.filter(EventName.OPPGAVE_FERDIGSTILT)
            .first()
            .toMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                DataFelt.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }
    }

    private fun bekreftMarkeringAvForespoerselSomBesvart() {
        verify(exactly = 1) {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson()
            )
        }
    }

    private object Mock {
        const val JOURNALPOST_ID = "journalpost-id-skoleboller"
        const val SAK_ID = "forundret-lysekrone"
        const val OPPGAVE_ID = "neglisjert-sommer"

        val forespoerselId: UUID = UUID.randomUUID()
        val innsending = mockInnsending().copy(identitetsnummer = "fnr-bjarne")
        val innsendtInntektsmelding = mapInntektsmelding(innsending, "Bjarne Betjent", "Bedrift A/S", "Max Mekker")
    }
}
