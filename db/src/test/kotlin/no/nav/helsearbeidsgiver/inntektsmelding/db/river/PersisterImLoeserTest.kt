package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PersisterImLoeserTest {

    private val testRapid = TestRapid()
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        PersisterImLoeser(testRapid, repository)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagreInntektsmelding(any(), any())
        } just Runs

        coEvery { repository.hentNyeste(any()) } returns null

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to randomUuid().toJson(),
            Key.FORESPOERSEL_ID to randomUuid().toJson(),
            Key.VIRKSOMHET to "Test Virksomhet".toJson(),
            Key.ARBEIDSGIVER_INFORMASJON to Mock.arbeidsgiver.toJson(PersonDato.serializer()),
            Key.ARBEIDSTAKER_INFORMASJON to Mock.arbeidstaker.toJson(PersonDato.serializer()),
            Key.INNTEKTSMELDING to Mock.innsending.toJson(Innsending.serializer())
        )

        coVerify(exactly = 1) {
            repository.lagreInntektsmelding(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INSENDING_STARTED
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe false

        Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), publisert)
            .shouldNotBeNull()
            .shouldBeEqualToIgnoringFields(Mock.inntektsmelding, Inntektsmelding::tidspunkt)
    }

    @Test
    fun `ikke lagre ved duplikat`() {
        coEvery { repository.hentNyeste(any()) } returns Mock.inntektsmelding.copy(tidspunkt = ZonedDateTime.now().minusHours(1).toOffsetDateTime())

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to randomUuid().toJson(),
            Key.FORESPOERSEL_ID to randomUuid().toJson(),
            Key.VIRKSOMHET to "Test Virksomhet".toJson(),
            Key.ARBEIDSGIVER_INFORMASJON to Mock.arbeidsgiver.toJson(PersonDato.serializer()),
            Key.ARBEIDSTAKER_INFORMASJON to Mock.arbeidstaker.toJson(PersonDato.serializer()),
            Key.INNTEKTSMELDING to Mock.innsending.copy(årsakInnsending = AarsakInnsending.ENDRING).toJson(Innsending.serializer())
        )

        coVerify(exactly = 0) {
            repository.lagreInntektsmelding(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INSENDING_STARTED
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe true
    }

    object Mock {
        val innsending = Innsending(
            orgnrUnderenhet = "orgnr-bål",
            identitetsnummer = "fnr-fredrik",
            behandlingsdager = listOf(LocalDate.now().plusDays(5)),
            egenmeldingsperioder = listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(2)
                )
            ),
            arbeidsgiverperioder = emptyList(),
            bestemmendeFraværsdag = LocalDate.now(),
            fraværsperioder = emptyList(),
            inntekt = Inntekt(
                bekreftet = true,
                beregnetInntekt = 32100.0,
                endringÅrsak = null,
                manueltKorrigert = false
            ),
            fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
                utbetalerFullLønn = true,
                begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert
            ),
            refusjon = Refusjon(
                utbetalerHeleEllerDeler = true,
                refusjonPrMnd = 200.0,
                refusjonOpphører = LocalDate.now()
            ),
            naturalytelser = listOf(
                Naturalytelse(
                    naturalytelse = NaturalytelseKode.KOSTDOEGN,
                    dato = LocalDate.now(),
                    beløp = 300.0
                )
            ),
            årsakInnsending = AarsakInnsending.NY,
            bekreftOpplysninger = true
        )

        val arbeidsgiver = PersonDato("Gudrun Arbeidsgiver", null, "fnr-gudrun")
        val arbeidstaker = PersonDato("Toril Arbeidstaker", null, innsending.identitetsnummer)

        val inntektsmelding = mapInntektsmelding(innsending, arbeidstaker.navn, "Test Virksomhet", arbeidsgiver.navn)
    }
}
