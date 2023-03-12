package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZonedDateTime

internal class PdfDokumentTest {

    val dag = LocalDate.of(2022, 12, 24)

    val im = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument(
        orgnrUnderenhet = "123456789",
        identitetsnummer = "12345678901",
        fulltNavn = "Ola Normann",
        virksomhetNavn = "Norge AS",
        behandlingsdager = listOf(dag),
        egenmeldingsperioder = listOf(
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(2)),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag.plusDays(3), dag.plusDays(4))
        ),
        beregnetInntekt = 25000.0.toBigDecimal(),
        fullLønnIArbeidsgiverPerioden = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden(
            true,
            begrunnelse = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT
        ),
        refusjon = Refusjon(true, 25000.0.toBigDecimal(), dag.plusDays(3)),
        naturalytelser = listOf(
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse(
                no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0.toBigDecimal()
            ),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse(
                no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0.toBigDecimal()
            )
        ),
        fraværsperioder = listOf(
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(55)),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(22)),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(32))
        ),
        arbeidsgiverperioder = listOf(
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(30)),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(40)),
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(dag, dag.plusDays(40))
        ),
        bestemmendeFraværsdag = dag.plusDays(90),
        tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
        årsakInnsending = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.NY,
        identitetsnummerInnsender = "123123123123123"
    )

    @Test
    fun `skal lage kvittering`() {
        // val file = File(System.getProperty("user.home"), "/Desktop/inntektsmelding.pdf")
        val file = File.createTempFile("kvittering", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
