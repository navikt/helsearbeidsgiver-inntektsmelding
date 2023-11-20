package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PersisterImLoeserTest {

    private val rapid = TestRapid()
    private var løser: PersisterImLoeser
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        løser = PersisterImLoeser(rapid, repository)
    }

    private fun sendMelding(melding: JsonMessage) {
        rapid.reset()
        rapid.sendTestMessage(melding.toJson())
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagreInntektsmelding(any(), any())
        } returns Unit
        coEvery { repository.hentNyeste(any()) } returns null
        sendMelding(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.BEHOV.str to BehovType.PERSISTER_IM.name,
                    DataFelt.VIRKSOMHET.str to "Test Virksomhet",
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str to PersonDato("Test person", null, ""),
                    DataFelt.ARBEIDSGIVER_INFORMASJON.str to PersonDato("Test person", null, ""),
                    Key.UUID.str to "uuid",
                    DataFelt.INNTEKTSMELDING.str to Mock.innsending
                )
            )
        )

        coVerify(exactly = 1) {
            repository.lagreInntektsmelding(any(), any())
        }
        val message = rapid.inspektør.message(0)
        Assertions.assertEquals(EventName.INSENDING_STARTED.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertNotNull(message.path(DataFelt.INNTEKTSMELDING.str).asText())
        Assertions.assertFalse(message.path(DataFelt.ER_DUPLIKAT_IM.str).asBoolean())
    }

    @Test
    fun `ikke lagre ved duplikat`() {
        coEvery { repository.hentNyeste(any()) } returns Mock.inntektsmelding.copy(tidspunkt = ZonedDateTime.now().minusHours(1).toOffsetDateTime())
        sendMelding(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.BEHOV.str to BehovType.PERSISTER_IM.name,
                    DataFelt.VIRKSOMHET.str to "Test Virksomhet",
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str to PersonDato("Test person", null, ""),
                    DataFelt.ARBEIDSGIVER_INFORMASJON.str to PersonDato("Test person 2", null, ""),
                    Key.UUID.str to "uuid",
                    DataFelt.INNTEKTSMELDING.str to Mock.innsending.copy(årsakInnsending = AarsakInnsending.ENDRING)
                )
            )
        )

        coVerify(exactly = 0) {
            repository.lagreInntektsmelding(any(), any())
        }
        val message = rapid.inspektør.message(0)
        Assertions.assertEquals(EventName.INSENDING_STARTED.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertTrue(message.path(DataFelt.ER_DUPLIKAT_IM.str).asBoolean())
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
        val arbeidstaker = PersonDato("Test person", null, innsending.identitetsnummer)
        val arbeidsgiver = PersonDato("Test person", null, innsending.identitetsnummer)
        val inntektsmelding = mapInntektsmelding(innsending, arbeidstaker.navn, "Test Virksomhet", arbeidsgiver.navn)
    }
}
