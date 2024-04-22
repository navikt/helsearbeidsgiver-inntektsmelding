package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.tilTrengerInntekt
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.mapInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingIT : EndToEndTest() {

    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.skjema.orgnrUnderenhet)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.INSENDING_STARTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = Mock.JOURNALPOST_ID,
            journalpostFerdigstilt = true,
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId
            publish(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.CLIENT_ID to UUID.randomUUID().toJson(),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to Mock.skjema.orgnrUnderenhet.toJson(),
                Key.IDENTITETSNUMMER to Mock.skjema.identitetsnummer.toJson(),
                Key.ARBEIDSGIVER_ID to Mock.skjema.identitetsnummer.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(Innsending.serializer())
            )
        }

        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .firstAsMap()
            .also {
                // Ble lagret i databasen
                it[Key.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }
        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                it[Key.ER_DUPLIKAT_IM]!!.fromJson(Boolean.serializer()) shouldBe false
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .also {
                // EVENT: Mottatt inntektsmelding
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .firstAsMap()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING_DOKUMENT
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .also {
                // Verifiser at inntektsmelding er distribuert på ekstern kafka
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID

                it[Key.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
            }

        bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak()

        bekreftMarkeringAvForespoerselSomBesvart()
    }

    @Test
    fun `skal ikke lagre duplikat inntektsmelding`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.skjema.orgnrUnderenhet)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        imRepository.lagreInntektsmelding(Mock.forespoerselId.toString(), Mock.innsendtInntektsmelding)

        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.INSENDING_STARTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = Mock.JOURNALPOST_ID,
            journalpostFerdigstilt = true,
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        coEvery { brregClient.hentVirksomhetNavn(any()) } returns "Bedrift A/S"

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId
            publish(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.CLIENT_ID to UUID.randomUUID().toJson(),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to Mock.skjema.orgnrUnderenhet.toJson(),
                Key.IDENTITETSNUMMER to "fnr-bjarne".toJson(),
                Key.ARBEIDSGIVER_ID to "fnr-max".toJson(),
                Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(Innsending.serializer())
            )
        }

        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                it[Key.ER_DUPLIKAT_IM]!!.fromJson(Boolean.serializer()) shouldBe true
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT).all() shouldHaveSize 0
    }

    private fun bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak() {
        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(BehovType.NOTIFIKASJON_HENT_ID)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.SAK_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.OPPGAVE_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }

        messages.filter(EventName.SAK_FERDIGSTILT)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages.filter(EventName.OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
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
        val skjema = mockInnsending().copy(identitetsnummer = "fnr-bjarne")

        private val forespoersel = skjema.tilTrengerInntekt(UUID.randomUUID())

        val innsendtInntektsmelding = mapInntektsmelding(
            forespoersel = forespoersel,
            skjema = skjema,
            fulltnavnArbeidstaker = "Bjarne Betjent",
            virksomhetNavn = "Bedrift A/S",
            innsenderNavn = "Max Mekker"
        )

        val forespoerselSvar = ForespoerselSvar.Suksess(
            type = ForespoerselType.KOMPLETT,
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr,
            vedtaksperiodeId = forespoersel.vedtaksperiodeId,
            egenmeldingsperioder = forespoersel.egenmeldingsperioder,
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            skjaeringstidspunkt = null,
            bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
            forespurtData = mockForespurtData(),
            erBesvart = false
        )
    }
}
