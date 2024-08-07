package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.tilForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.mapInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.maxMekker
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
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

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.JOURNALPOST_ID,
                journalpostFerdigstilt = true,
                melding = "Ha en fin dag!",
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.skjema.identitetsnummer.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(Innsending.serializer()),
                ).toJson(),
        )

        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.INNTEKTSMELDING, nestedData = true)
            .filter(Key.ER_DUPLIKAT_IM, nestedData = true)
            .firstAsMap()
            .also {
                // Ble lagret i databasen
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.INNTEKTSMELDING].shouldNotBeNull()
                data[Key.ER_DUPLIKAT_IM].shouldNotBeNull().fromJson(Boolean.serializer()).shouldBeFalse()
            }

        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .also {
                // EVENT: Mottatt inntektsmelding
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .firstAsMap()
            .also {
                // Journalført i dokarkiv
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
            }

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING_DOKUMENT
                it[Key.JOURNALPOST_ID]?.fromJsonToString() shouldBe Mock.JOURNALPOST_ID
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
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
        imRepository.lagreInntektsmelding(Mock.forespoerselId, Mock.innsendtInntektsmelding)

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.JOURNALPOST_ID,
                journalpostFerdigstilt = true,
                melding = "Ha en fin dag!",
                dokumenter = emptyList(),
            )

        coEvery { brregClient.hentVirksomhetNavn(any()) } returns "Bedrift A/S"

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to maxMekker.ident!!.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(Innsending.serializer()),
                ).toJson(),
        )

        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM, nestedData = true)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM].shouldNotBeNull().fromJson(Boolean.serializer()).shouldBeTrue()
            }

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT).all() shouldHaveSize 0
    }

    private fun bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak() {
        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(BehovType.NOTIFIKASJON_HENT_ID)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.SAK_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.OPPGAVE_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }

        messages
            .filter(EventName.SAK_FERDIGSTILT)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages
            .filter(EventName.OPPGAVE_FERDIGSTILT)
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
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            )
        }
    }

    private object Mock {
        const val JOURNALPOST_ID = "journalpost-id-skoleboller"
        const val SAK_ID = "forundret-lysekrone"
        const val OPPGAVE_ID = "neglisjert-sommer"

        val forespoerselId: UUID = UUID.randomUUID()
        val skjema = mockInnsending().copy(identitetsnummer = bjarneBetjent.ident!!)

        private val forespoersel = skjema.tilForespoersel(UUID.randomUUID())

        val innsendtInntektsmelding =
            mapInntektsmelding(
                forespoersel = forespoersel,
                skjema = skjema,
                fulltnavnArbeidstaker = bjarneBetjent.navn.fulltNavn(),
                virksomhetNavn = "Bedrift A/S",
                innsenderNavn = maxMekker.navn.fulltNavn(),
            )

        val forespoerselSvar =
            ForespoerselSvar.Suksess(
                type = ForespoerselType.KOMPLETT,
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                egenmeldingsperioder = forespoersel.egenmeldingsperioder,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
                skjaeringstidspunkt = null,
                bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )
    }
}
