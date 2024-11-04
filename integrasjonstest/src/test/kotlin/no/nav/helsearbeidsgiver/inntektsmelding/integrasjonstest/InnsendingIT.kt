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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
                    Key.ARBEIDSGIVER_FNR to Mock.forespoersel.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                ).toJson(),
        )

        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                // Ble lagret i databasen
                val data = it[Key.DATA].shouldNotBeNull().toMap()
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
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID
            }

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID
            }

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .also {
                // Verifiser at inntektsmelding er distribuert på ekstern kafka
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID

                it[Key.INNTEKTSMELDING].shouldNotBeNull()
            }

        bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak()

        bekreftMarkeringAvForespoerselSomBesvart()
    }

    @Test
    fun `skal ikke lagre duplikat inntektsmelding`() {
        imRepository.lagreInntektsmeldingSkjema(Mock.skjema)

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.forespoersel.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                ).toJson(),
        )

        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM].shouldNotBeNull().fromJson(Boolean.serializer()).shouldBeTrue()
            }

        messages.filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT).all() shouldHaveSize 0
    }

    @Test
    fun `skal ikke lagre duplikat inntektsmeldingskjema`() {
        imRepository.lagreInntektsmeldingSkjema(Mock.skjema)

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.forespoersel.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                ).toJson(),
        )

        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM].shouldNotBeNull().fromJson(Boolean.serializer()).shouldBeTrue()
            }

        messages.filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_MOTTATT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALFOERT).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET).all() shouldHaveSize 0

        messages.filter(EventName.INNTEKTSMELDING_DISTRIBUERT).all() shouldHaveSize 0
    }

    private fun bekreftForventedeMeldingerForFerdigstilligAvOppgaveOgSak() {
        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }
    }

    private fun bekreftMarkeringAvForespoerselSomBesvart() {
        verify(exactly = 1) {
            priProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            )
        }
    }

    private object Mock {
        const val JOURNALPOST_ID = "journalpost-id-skoleboller"

        val skjema = mockSkjemaInntektsmelding()

        val forespoerselId: UUID = skjema.forespoerselId

        private val orgnr = Orgnr.genererGyldig()

        val forespoersel =
            Forespoersel(
                orgnr = orgnr.verdi,
                fnr = bjarneBetjent.ident!!,
                vedtaksperiodeId = UUID.randomUUID(),
                sykmeldingsperioder =
                    listOf(
                        1.juli til 12.juli,
                        15.juli til 2.august,
                    ),
                egenmeldingsperioder =
                    listOf(
                        26.juni til 27.juni,
                        29.juni til 29.juni,
                    ),
                bestemmendeFravaersdager = mapOf(orgnr.verdi to 15.juli),
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )

        val forespoerselSvar =
            ForespoerselFraBro(
                orgnr = Orgnr(forespoersel.orgnr),
                fnr = Fnr(forespoersel.fnr),
                forespoerselId = forespoerselId,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                egenmeldingsperioder = forespoersel.egenmeldingsperioder,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
                bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager.mapKeys { Orgnr(it.key) },
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )
    }
}
